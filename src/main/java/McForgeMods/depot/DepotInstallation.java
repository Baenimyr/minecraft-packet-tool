package McForgeMods.depot;

import McForgeMods.PaquetMinecraft;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import org.apache.commons.vfs2.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
	private static final String DATABASE = ".mods.json";
	
	private final FileSystemManager filesystem;
	private final FileObject        minecraft_dir;
	
	private final HashMap<String, Installation> installations = new HashMap<>();
	/**
	 * Version de minecraft pour l'installation.
	 */
	public        Version                       mcversion     = null;
	public        Version                       forge         = null;
	
	
	/**
	 * Ouvre un dossier pour l'installation des mods. Par défaut, ce dossier est ~/.minecraft/mods.
	 *
	 * @param dossier: dossier .minecraft d'installation
	 */
	public DepotInstallation(URI dossier) throws FileSystemException {
		this.filesystem = VFS.getManager();
		this.minecraft_dir = this.filesystem.resolveFile(dossier);
	}
	
	private static URI local(Path dossier) {
		while (!dossier.getRoot().equals(dossier)) {
			if (dossier.getFileName().equals(dossier.getFileSystem().getPath(".minecraft"))) {
				return dossier.toUri();
			}
			dossier = dossier.getParent();
		}
		return Paths.get(System.getProperty("user.home"), ".minecraft").toUri();
	}
	
	public static DepotInstallation depot(Path dossier) throws FileSystemException {
		final URI dos;
		if (dossier == null) {
			dos = local(Paths.get("").toAbsolutePath());
		} else if (dossier.startsWith("~"))
			dos = Paths.get(System.getProperty("user.home")).resolve(dossier.subpath(1, dossier.getNameCount()))
					.toUri();
		else dos = dossier.toUri();
		
		return new DepotInstallation(dos);
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
		
		if (this.installations.containsKey(mversion.modid)) this.installations.get(mversion.modid).manuel = manuel;
		else {
			final Installation installation = new Installation(mversion);
			installation.manuel = manuel;
			this.installations.put(mversion.modid, installation);
		}
	}
	
	/**
	 * Télécharge le paquet.
	 * <p>
	 * Si le paquet est déjà présent dans le cache, il ne sera pas téléchargé à nouveau. Après le téléchargement ou non,
	 * vérifie la somme de contrôle.
	 *
	 * @param dossier_cache est le dossier sur le système de fichier local où entroposer les fichiers téléchargés.
	 * @param paquet à installer
	 * @param archive_metadata données associées à l'archive: somme de contrôle
	 * @return le lien vers le paquet dans le cache, ou {@link Optional#empty()} en cas d'échec
	 */
	public Optional<URI> telechargementPaquet(final FileObject dossier_cache, final PaquetMinecraft paquet,
			PaquetMinecraft.FichierMetadata archive_metadata) {
		try {
			FileObject dest = dossier_cache.resolveFile(paquet.modid + "-" + paquet.version.toString() + ".tar");
			
			if (!dest.exists()) {
				// téléchargement
				FileObject src = filesystem.resolveFile(archive_metadata.path);
				dest.copyFrom(src, new FileDepthSelector());
			}
			
			try (InputStream dest_is = dest.getContent().getInputStream()) {
				if (!archive_metadata.checkSHA(dest_is)) {
					System.err.printf("[ERROR] mauvaise somme de contrôle de '%s'%n", dest);
					return Optional.empty();
				}
			} catch (IOException e) {
				System.err.printf("[Install] [ERROR] impossible de vérifier l'intégrité de l'archive: %s%n",
						dest.getPublicURIString());
				return Optional.empty();
			}
			
			return Optional.of(dest.getURL().toURI());
		} catch (FileSystemException | URISyntaxException io) {
			System.err.printf("[Install] impossible de télécharger l'archive pour %s%n", paquet);
			System.err.println("\t" + io.getClass() + ":" + io.getMessage());
			return Optional.empty();
		}
	}
	
	/**
	 * Extrait et vérifie les fichiers de l'archive d'installation.
	 *
	 * @param archive_url: lien vers l'archive
	 */
	public void ouverturePaquet(final URI archive_url) throws IOException {
		FileObject archive_f = filesystem.resolveFile(archive_url);
		final FileObject archive_tar = filesystem.createFileSystem("tar", archive_f);
		FileObject mods = archive_tar.resolveFile(PaquetMinecraft.INFOS);
		
		final PaquetMinecraft modVersion;
		try (InputStream is = mods.getContent().getInputStream()) {
			modVersion = PaquetMinecraft.lecturePaquet(is);
		}
		
		FileObject data = archive_tar.resolveFile(PaquetMinecraft.FICHIERS);
		for (final PaquetMinecraft.FichierMetadata metadata : modVersion.fichiers) {
			try (final FileObject dest = minecraft_dir.resolveFile(metadata.path);
				 final FileObject src = data.resolveFile(metadata.path)) {
				// copie les fichiers déclarés
				dest.copyFrom(src, new FileDepthSelector());
			}
		}
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
	public boolean verificationIntegrite(final PaquetMinecraft paquet) throws FileSystemException {
		for (PaquetMinecraft.FichierMetadata metadata : paquet.fichiers) {
			final FileObject fichier = minecraft_dir.resolveFile(metadata.path);
			if (!fichier.exists()) return false;
			try (final InputStream is = fichier.getContent().getInputStream()) {
				metadata.checkSHA(is);
			} catch (IOException e) {
				System.err.printf("Impossible de lire le fichier '%s'%n", fichier.getName().getPath());
				return false;
			}
		}
		return true;
	}
	
	// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- Désinstallation +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-
	
	/**
	 * Désinstalle une version de mod.
	 * <p>
	 * Cette fonction supprime les fichiers installés par le paquet puis toute information conservée localement au sujet
	 * du paquet.
	 *
	 * @return {@code true} si le paquet à bien été supprimé
	 */
	public boolean desinstallation(String id) throws FileSystemException {
		if (this.installations.containsKey(id)) {
			final Installation installation = this.installations.get(id);
			final PaquetMinecraft modVersion = installation.paquet;
			this.suppressionFichiers(modVersion);
			this.installations.remove(id);
			return true;
		}
		return false;
	}
	
	private void suppressionFichiers(final PaquetMinecraft paquet) throws FileSystemException {
		for (PaquetMinecraft.FichierMetadata fichier : paquet.fichiers) {
			FileObject f = minecraft_dir.resolveFile(fichier.path);
			
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
	public void statusImportation() throws FileSystemException {
		FileObject infos = minecraft_dir.resolveFile(DATABASE);
		if (infos.exists()) {
			try (InputStream fis = infos.getContent().getInputStream()) {
				JSONObject racine = new JSONObject(new JSONTokener(fis));
				
				if (racine.has("config")) {
					JSONObject config = racine.getJSONObject("config");
					this.mcversion = Version.read(config.getString("minecraft"));
					this.forge = config.has("forge") ? Version.read(config.getString("forge")) : null;
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
						} catch (JSONException jsonException) {
							System.err.printf("[ERREUR] %s: %s%n", infos.getURL(), jsonException.getMessage());
						}
					}
				}
			} catch (IOException io) {
				System.err.println(io.getClass() + ":" + io.getLocalizedMessage());
			}
		}
	}
	
	public void statusSauvegarde() throws IOException {
		FileObject infos = minecraft_dir.resolveFile(DATABASE);
		try (OutputStream fos = infos.getContent().getOutputStream();
			 BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos))) {
			JSONObject data = new JSONObject();
			
			if (this.mcversion != null) {
				JSONObject config = new JSONObject();
				config.put("minecraft", this.mcversion.toString());
				if (forge != null) config.put("forge", forge.toString());
				data.put("config", config);
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
		public       boolean         manuel = true;
		public       boolean         verrou = false;
		
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
			return this.paquet.equals(((Installation) o).paquet);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.paquet);
		}
	}
}
