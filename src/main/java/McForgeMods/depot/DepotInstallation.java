package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

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
	public final  Path                      dossier;
	public final  DepotLocal                depot;
	private final Map<String, Installation> installations = new HashMap<>();
	/**
	 * Version de minecraft pour l'installation.
	 */
	public        VersionIntervalle         mcversion     = null;
	
	
	/**
	 * Ouvre un dossier pour l'installation des mods. Par défaut, ce dossier est ~/.minecraft/mods.
	 *
	 * @param dossier d'installation ou {@code null}
	 */
	public DepotInstallation(DepotLocal depot, Path dossier) {
		this.depot = depot;
		Path dos = Path.of(System.getProperty("user.home")).resolve(".minecraft");
		if (dossier == null) {
			Path d = Path.of("").toAbsolutePath();
			int i;
			for (i = d.getNameCount() - 1; i >= 0; i--) {
				if (d.getName(i).toString().equals(".minecraft")) {
					dos = d.subpath(0, i + 1);
					break;
				}
			}
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
	public void installation(ModVersion mversion, boolean manuel) {
		this.statusChange(mversion, manuel);
	}
	
	/**
	 * Désinstalle une version de mod.
	 * <p>
	 * Si la cible est toujours nécessaire en temps que dépendance, elle passe en installation automatique. Cette
	 * fonction désinstalle même si cette version est la dépendance d'un autre mod.
	 */
	public void desinstallation(String id) {
		if (this.installations.containsKey(id)) {
			Installation installation = this.installations.get(id);
			Optional<ModVersion> modVersion = this.depot.getModVersion(installation.modid, installation.version);
			if (modVersion.isPresent()) {
				// TODO: suppression fichiers
			}
			
			this.statusSuppression(id);
		}
	}
	
	/**
	 * Détecte les conflits: même modid mais versions différentes, et supprime les fichiers en trop.
	 * <p>
	 * Deux mods sont en conflit si les modid et la version minecraft sont identiques mais que la version est
	 * différente. Considère seulement les versions pouvant être en conflit avec {@code statique}.
	 *
	 * @param statique version à conserver
	 */
	public void suppressionConflits(ModVersion statique) {
		if (this.installations.containsKey(statique.modid)
				&& this.installations.get(statique.modid).version != statique.version) {
			this.desinstallation(statique.modid);
		}
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
		for (ArchiveMod resultat : ArchiveMod.analyseDossier(dossier.resolve("mods"), this.depot)) {
			final Mod mod = resultat.mod;
			ModVersion modVersion = resultat.modVersion;
			modVersion = this.depot.ajoutModVersion(modVersion);
			
			Installation installation;
			if (this.installations.containsKey(modVersion.modid) && this.installations.get(modVersion.modid).version
					.equals(modVersion.version)) installation = this.installations.get(modVersion.modid);
			else {
				installation = new Installation(modVersion.modid, modVersion.version);
				installation.manuel = true;
				this.installations.put(modVersion.modid, installation);
			}
			installation.fichier = dossier.resolve("mods").relativize(Path.of(resultat.fichier.getAbsolutePath()))
					.toString();
		}
	}
	
	public Installation installation(String id) {
		return this.installations.get(id);
	}
	
	public boolean estManuel(ModVersion version) {
		return this.installations.containsKey(version.modid) && this.installations.get(version.modid).manuel;
	}
	
	/** Change le status associé à une version de mod. */
	public void statusChange(ModVersion version, boolean manuel) {
		if (this.installations.containsKey(version.modid)) {
			Installation i = this.installations.get(version.modid);
			i.manuel = manuel;
		} else {
			Installation i = new Installation(version.modid, version.version);
			i.manuel = manuel;
			this.installations.put(version.modid, i);
		}
	}
	
	public boolean estVerrouille(ModVersion version) {
		return this.installations.containsKey(version.modid) && this.installations.get(version.modid).verrou;
	}
	
	public void verrouillerMod(ModVersion version, boolean verrou) {
		if (this.installations.containsKey(version.modid)) {
			Installation i = this.installations.get(version.modid);
			i.verrou = verrou;
		} else {
			Installation i = new Installation(version.modid, version.version);
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
				Installation i = this.installation(dep.getKey());
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
		File infos = dossier.resolve("mods").resolve(".mods.json").toFile();
		if (infos.exists()) {
			try (FileInputStream fis = new FileInputStream(infos)) {
				JSONObject racine = new JSONObject(new JSONTokener(fis));
				
				if (racine.has("minecraft")) {
					JSONObject minecraft = racine.getJSONObject("minecraft");
					this.mcversion = VersionIntervalle.read(minecraft.getString("version"));
				}
				
				if (racine.has("mods")) {
					JSONObject mods = racine.getJSONObject("mods");
					for (String modid : mods.keySet()) {
						JSONObject mod_data = mods.getJSONObject(modid);
						if (mod_data != null) {
							try {
								final Version version = Version.read(mod_data.getString("version"));
								final boolean manual = mod_data.getBoolean("manual");
								final boolean verrou = mod_data.getBoolean("locked");
								
								Installation i = new Installation(modid, version);
								i.manuel = manual;
								i.verrou = verrou;
								i.fichier = mod_data.getString("fichier");
								this.installations.put(modid, i);
							} catch (NullPointerException ignored) {
							} catch (JSONException jsonException) {
								jsonException.printStackTrace();
							}
						}
					}
				}
			} catch (IOException io) {
				System.err.println(io.getClass() + ":" + io.getLocalizedMessage());
			}
		}
	}
	
	public void statusSauvegarde() throws IOException {
		File infos = dossier.resolve("mods").resolve(".mods.json").toFile();
		try (FileOutputStream fos = new FileOutputStream(infos);
			 BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos))) {
			JSONObject data = new JSONObject();
			
			if (this.mcversion != null) {
				JSONObject minecraft = new JSONObject();
				minecraft.put("version", this.mcversion.toString());
				data.put("minecraft", minecraft);
			}
			
			JSONObject MODS = new JSONObject();
			for (Map.Entry<String, Installation> entry : this.installations.entrySet()) {
				JSONObject mod_data = new JSONObject();
				Installation i = entry.getValue();
				mod_data.put("version", i.version.toString());
				mod_data.put("fichier", i.fichier);
				mod_data.put("manual", i.manuel);
				mod_data.put("locked", i.verrou);
				
				MODS.put(i.modid, mod_data);
			}
			data.put("mods", MODS);
			data.write(bw, 4, 4);
		}
	}
	
	public Collection<String> getModids() {
		return this.installations.keySet();
	}
	
	public boolean contains(String modid) {
		return this.installations.containsKey(modid);
	}
	
	@Override
	public void close() throws IOException {
		this.statusSauvegarde();
	}
	
	/**
	 * Toutes les informations pour la gestion des installations.
	 * <p>
	 * Un {@link #modid} ne peut être installé que sous une unique {@link #version}. Toute version en conflit doit être
	 * supprimée avant. L'installation peut être manuelle ou automatique. Une installation verrouillée ne peut pas être
	 * modifiée.
	 */
	public static class Installation {
		public final String  modid;
		public final Version version;
		public       String  fichier;
		public       boolean manuel = true;
		public       boolean verrou = false;
		
		public Installation(String modid, Version version) {
			Objects.requireNonNull(modid);
			Objects.requireNonNull(version);
			this.modid = modid.intern();
			this.version = version;
		}
		
		public Installation(ModVersion modVersion) {
			this(modVersion.modid, modVersion.version);
		}
		
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Installation that = (Installation) o;
			return modid.equals(that.modid) && version.equals(that.version);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(modid, version);
		}
	}
}
