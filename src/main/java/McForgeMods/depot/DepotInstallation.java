package McForgeMods.depot;

import McForgeMods.PaquetMinecraft;
import McForgeMods.VersionIntervalle;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ce dépot lit les informations directement dans les fichiers qu'il rencontre. Il permet d'analyser une instance
 * minecraft (un dossier .minecraft) pour déduire les mods présents. Ensuite toutes les actions d'un dépot sont
 * applicables: présence de mod, présence de version, ...
 * <p>
 * L'unique contrainte avec un dépot d'installation, est qu'il ne peut pas y avoir plus d'un seul fichier par modid et
 * version minecraft dans les dossiers <i>mods</i> ou <i>mods/mcversion</i>. Autrement, il y a conflit lors du
 * chargement du jeu. Seulement si les versions de mod sont différentes, le dépot d'installation sera capable de
 * détecter cette erreur.
 */
public class DepotInstallation implements Closeable {
	public final  Path                          dossier;
	public final  DepotLocal                    depot;
	private final HashMap<String, Installation> installations = new HashMap<>();
	/**
	 * Version de minecraft pour l'installation.
	 */
	public        VersionIntervalle             mcversion     = null;
	
	
	/**
	 * Ouvre un dossier pour l'installation des mods. Par défaut, ce dossier est ~/.minecraft/mods.
	 *
	 * @param dossier d'installation ou {@code null}
	 */
	public DepotInstallation(DepotLocal depot, Path dossier) {
		this.depot = depot;
		Path dos = Path.of(System.getProperty("user.home")).resolve(".minecraft");
		if (dossier == null) {
			Path d = Path.of(".").toAbsolutePath();
			while (d.getParent() != null && !d.getFileName().endsWith(".minecraft")) d = d.getParent();
			if (d.getFileName().endsWith(".minecraft")) dos = d;
		} else if (dossier.startsWith("~")) {
			dos = Path.of(System.getProperty("user.home")).resolve(dossier.subpath(1, dossier.getNameCount()));
		} else {
			dos = dossier.toAbsolutePath();
		}
		this.dossier = dos;
	}
	
	/**
	 * Marque un mod comme installé.
	 * <p>
	 * Le téléchargement du fichier est effectué en amont. Cette action ne doit être réalisée que si le fichier est
	 * maintenant présent.
	 *
	 * @param manuel: si l'installation est manuelle ou automatique
	 */
	public void installation(PaquetMinecraft mversion, boolean manuel) {
		this.statusChange(mversion, manuel);
	}
	
	// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- Désinstallation +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-
	
	/**
	 * Désinstalle une version de mod.
	 * <p>
	 * Si la cible est toujours nécessaire en temps que dépendance, elle passe en installation automatique. Cette
	 * fonction désinstalle même si cette version est la dépendance d'un autre mod.
	 *
	 * @return {@code true} si le paquet à bien été supprimé
	 */
	public boolean desinstallation(String id) throws FileSystemException {
		if (this.installations.containsKey(id)) {
			Installation installation = this.installations.get(id);
			PaquetMinecraft modVersion = installation.paquet;
			this.suppressionFichiers(modVersion);
			this.statusSuppression(id);
			return true;
		}
		return false;
	}
	
	/**
	 * Supprime les fichiers associées à un paquet.
	 * <p>
	 * Tous les fichiers déclarés dans le paquet seront supprimés du dossier minecraft, s'ils existent toujours.
	 *
	 * @param paquet: paquet à effacer
	 * @throws FileSystemException si les fichiers ne peuvent pas être modifiés. Cette erreur peut laisser
	 * l'installation dans un état incohérent.
	 */
	void suppressionFichiers(final PaquetMinecraft paquet) throws FileSystemException {
		FileSystemManager filesystem = VFS.getManager();
		final FileObject minecraft = filesystem.resolveFile(dossier.toUri());
		for (PaquetMinecraft.FichierMetadata fichier : paquet.fichiers) {
			FileObject f = minecraft.resolveFile(fichier.path);
			
			if (f.exists() /* && hors config */) {
				f.delete();
			}
		}
	}
	
	/**
	 * Détecte les conflits: même modid mais versions différentes, et supprime les fichiers en trop.
	 * <p>
	 * Deux mods sont en conflit si les modid et la version minecraft sont identiques mais que la version est
	 * différente. Considère seulement les versions pouvant être en conflit avec {@code statique}.
	 *
	 * @param statique version à conserver
	 * @return {@code true} s'il ne reste aucun conflit.
	 */
	public boolean suppressionConflits(PaquetMinecraft statique) throws FileSystemException {
		if (this.installations.containsKey(statique.modid)
				&& this.installations.get(statique.modid).paquet.version != statique.version) {
			return this.desinstallation(statique.modid);
		}
		return true;
	}
	
	/**
	 * Vérifie l'integrité d'une installation.
	 * <p>
	 * Les fichiers déclarés dans {@link PaquetMinecraft#fichiers} doivent être présents, il s'il dispose d'une somme de
	 * contrôle elle doit être toujours valide.
	 *
	 * @param paquet: installation à vérifier
	 * @return {@code false} à la moindre erreur.
	 */
	public boolean verificationIntegrite(PaquetMinecraft paquet) {
		for (PaquetMinecraft.FichierMetadata fichier : paquet.fichiers) {
			final Path chemin_absolu = this.dossier.resolve(fichier.path);
			if (!Files.exists(chemin_absolu)) return false;
		}
		return true;
	}
	
	/**
	 * Parcours le sous-dossier `mods` pour trouver les fichiers de mods présents.
	 * <p>
	 * Les informations relatives au mod sont fusionnées avec celles déjà connues. Les informations relatives à la
	 * version de ce mod sont associées au fichier dont elles sont originaires. Aucune information ne provenant pas des
	 * fichiers n'est ajoutée.
	 * <p>
	 * Si un fichier d'un mod non installé est trouvé, il est marqué comme installé manuellement.
	 */
	public void analyseDossier() {
		this.statusImportation();
		for (ArchiveMod resultat : ArchiveMod.analyseDossier(dossier.resolve("mods"))) {
			PaquetMinecraft modVersion = resultat.modVersion;
			if (!this.depot.ajoutModVersion(modVersion)) continue;
			
			Installation installation;
			if (this.installations.containsKey(modVersion.modid) && this.installations.get(modVersion.modid).paquet
					.equals(modVersion)) installation = this.installations.get(modVersion.modid);
			else {
				installation = new Installation(modVersion);
				installation.manuel = true;
				this.installations.put(modVersion.modid, installation);
			}
			installation.fichier = dossier.relativize(Path.of(resultat.fichier.getAbsolutePath())).toString();
		}
	}
	
	// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+- Accès aux informations de l'installation +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-
	
	public Collection<String> getModids() {
		return this.installations.keySet();
	}
	
	public Installation informations(String modid) {
		return this.installations.get(modid);
	}
	
	public PaquetMinecraft getInstallation(String modid) {
		return this.installations.get(modid).paquet;
	}
	
	public boolean contains(String modid) {
		return this.installations.containsKey(modid);
	}
	
	public boolean estManuel(PaquetMinecraft version) {
		return this.installations.containsKey(version.modid) && this.installations.get(version.modid).manuel();
	}
	
	/** Change le status associé à une version de mod. */
	public void statusChange(PaquetMinecraft version, boolean manuel) {
		if (this.installations.containsKey(version.modid)) {
			Installation i = this.installations.get(version.modid);
			i.manuel = manuel;
		} else {
			Installation i = new Installation(version);
			i.manuel = manuel;
			this.installations.put(version.modid, i);
		}
	}
	
	public boolean estVerrouille(PaquetMinecraft version) {
		return this.installations.containsKey(version.modid) && this.installations.get(version.modid).verrou;
	}
	
	public void verrouillerMod(PaquetMinecraft version, boolean verrou) {
		if (this.installations.containsKey(version.modid)) {
			Installation i = this.installations.get(version.modid);
			i.verrou = verrou;
		} else {
			Installation i = new Installation(version);
			i.verrou = verrou;
			this.installations.put(version.modid, i);
		}
	}
	
	/** Efface le status associé à une version de mod. */
	public void statusSuppression(String modid) {
		this.installations.remove(modid);
	}
	
	/**
	 * Fait la liste des versions absentes.
	 * <p>
	 * Parmis les dépendances fournies en entrée, cherche dans le dépot, si une version compatible existe. !!! Ne
	 * compare pas les versions minecraft, un intervalle ouverte sur la droite est une mauvaise idée.
	 *
	 * @return une map {modid -> version} des demandes qui n'ont pas trouvée de correspondance.
	 */
	public Map<String, VersionIntervalle> dependancesAbsentes(final Map<String, VersionIntervalle> demande) {
		final Map<String, VersionIntervalle> absents = new HashMap<>();
		for (Map.Entry<String, VersionIntervalle> dep : demande.entrySet()) {
			if (!this.contains(dep.getKey())) {
				PaquetMinecraft i = this.getInstallation(dep.getKey());
				if (!dep.getValue().correspond(i.version)) absents.put(dep.getKey(), dep.getValue());
			}
		}
		return absents;
	}
	
	/**
	 * Importe les informations sur le status d'installation.
	 * <p>
	 * Tous les status sont importés même si les mods ont disparus. Un mod pourrait ne pas être détecté ou ce serait le
	 * résultat d'une mauvaise manipulation, la restauration de l'installation doit rester possible.
	 */
	public void statusImportation() {
		File infos = dossier.resolve(".mods.json").toFile();
		if (infos.exists()) {
			try (FileInputStream fis = new FileInputStream(infos)) {
				JSONObject racine = new JSONObject(new JSONTokener(fis));
				
				if (racine.has("minecraft")) {
					JSONObject minecraft = racine.getJSONObject("minecraft");
					this.mcversion = VersionIntervalle.read(minecraft.getString("version"));
				}
				
				if (racine.has("mods")) {
					JSONArray mods = racine.getJSONArray("mods");
					for (int i = 0; i < mods.length(); i++) {
						JSONObject mod_data = mods.getJSONObject(i);
						try {
							PaquetMinecraft paquet = PaquetMinecraft.lecturePaquet(mod_data);
							final boolean manual = mod_data.getBoolean("manual");
							final boolean verrou = mod_data.getBoolean("locked");
							
							Installation inst = new Installation(paquet);
							inst.manuel = manual;
							inst.verrou = verrou;
							this.installations.put(paquet.modid, inst);
							
							if (!depot.contains(paquet)) depot.ajoutModVersion(paquet);
						} catch (JSONException jsonException) {
							jsonException.printStackTrace();
						}
					}
				}
			} catch (IOException io) {
				System.err.println(io.getClass() + ":" + io.getLocalizedMessage());
			}
		}
	}
	
	public void statusSauvegarde() throws IOException {
		File infos = dossier.resolve(".mods.json").toFile();
		try (FileOutputStream fos = new FileOutputStream(infos);
			 BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos))) {
			JSONObject data = new JSONObject();
			
			if (this.mcversion != null) {
				JSONObject minecraft = new JSONObject();
				minecraft.put("version", this.mcversion.toString());
				data.put("minecraft", minecraft);
			}
			
			JSONArray MODS = new JSONArray();
			for (Map.Entry<String, Installation> entry : this.installations.entrySet()) {
				Installation i = entry.getValue();
				PaquetMinecraft paquet = i.paquet;
				JSONObject i_data = new JSONObject();
				paquet.ecriturePaquet(i_data);
				i_data.put("manual", i.manuel());
				i_data.put("locked", i.verrou());
				
				MODS.put(i_data);
			}
			data.put("mods", MODS);
			data.write(bw, 4, 4);
		}
	}
	
	@Override
	public void close() throws IOException {
		this.statusSauvegarde();
	}
	
	/**
	 * Toutes les informations pour la gestion des installations.
	 * <p>
	 * Un modid ne peut être installé que sous une unique version. Toute version en conflit doit être supprimée avant.
	 * L'installation peut être manuelle ou automatique. Une installation verrouillée ne peut pas être modifiée.
	 */
	public static class Installation {
		public final PaquetMinecraft paquet;
		public       String          fichier;
		private      boolean         manuel = true;
		private      boolean         verrou = false;
		
		public Installation(PaquetMinecraft paquet) {
			Objects.requireNonNull(paquet);
			this.paquet = paquet;
		}
		
		public final boolean manuel() {
			return this.manuel;
		}
		
		public final boolean verrou() {
			return this.verrou;
		}
		
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Installation that = (Installation) o;
			return this.paquet.equals(((Installation) o).paquet);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.paquet);
		}
	}
}
