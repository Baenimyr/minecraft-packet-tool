package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.Version;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
public class DepotInstallation extends Depot implements Closeable {
	public final Path                      dossier;
	public final Map<String, Installation> installations = new HashMap<>();
	/**
	 * Version de minecraft pour l'installation.
	 */
	public       Version                   mcversion     = null;
	
	
	/**
	 * Ouvre un dossier pour l'installation des mods. Par défaut, ce dossier est ~/.minecraft/mods.
	 *
	 * @param dossier d'installation ou {@code null}
	 */
	public DepotInstallation(Path dossier) {
		if (dossier == null) {
			Path d = Path.of("").toAbsolutePath();
			for (int i = d.getNameCount() - 1; i >= 0; i--)
				if (d.getName(i).toString().equals(".minecraft")) {
					this.dossier = d.subpath(0, i + 1);
					return;
				}
			this.dossier = Path.of(System.getProperty("user.home")).resolve(".minecraft");
		} else if (dossier.startsWith("~")) {
			this.dossier = Path.of(System.getProperty("user.home")).resolve(dossier.subpath(1, dossier.getNameCount()));
		} else {
			this.dossier = dossier.toAbsolutePath();
		}
	}
	
	/**
	 * Marque un mod comme installé.
	 * <p>
	 * Le téléchargement du fichier est effectué en amont. Cette action ne doit être réalisée que si le fichier est
	 * maintenant présent.
	 *
	 * @param status de l'installation
	 */
	public void installation(ModVersion mversion, StatusInstallation status) {
		this.statusChange(mversion, status);
	}
	
	/**
	 * Désinstalle une version de mod.
	 * <p>
	 * Si la cible est toujours nécessaire en temps que dépendance, elle passe en installation automatique. Cette
	 * fonction désinstalle même si cette version est la dépendance d'un autre mod.
	 */
	public void desinstallation(ModVersion mversion) {
		this.statusChange(mversion, StatusInstallation.AUTO);
		if (this.contains(mversion.modid)) {
			this.mod_version.get(mversion.modid).remove(mversion);
			
			// Suppression des fichiers
			for (URL url : mversion.urls) {
				try {
					if (url.getProtocol().equals("file") && Path.of(url.toURI()).startsWith(this.dossier))
						Files.deleteIfExists(Path.of(url.toURI()));
				} catch (IOException | URISyntaxException ignored) {
				}
			}
			
			this.statusSuppression(mversion);
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
		if (this.contains(statique.modid)) {
			List<ModVersion> perimes = this.getModVersions(statique.modid).stream()
					.filter(mv -> mv.mcversion.equals(statique.mcversion) && !mv.version.equals(statique.version))
					.collect(Collectors.toList());
			perimes.forEach(this::desinstallation);
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
	public void analyseDossier(Depot infos) {
		this.statusImportation();
		for (ArchiveMod resultat : ArchiveMod.analyseDossier(dossier.resolve("mods"), infos)) {
			try {
				final Mod mod = resultat.mod;
				final ModVersion modVersion = resultat.modVersion;
				modVersion.ajoutURL(resultat.fichier.getAbsoluteFile().toURI().toURL());
				modVersion.ajoutAlias(resultat.fichier.getName());
				this.getMod(mod.modid).fusion(mod);
				this.ajoutModVersion(modVersion);
				
				Installation installation;
				if (this.installations.containsKey(modVersion.modid)) {
					installation = this.installations.get(modVersion.modid);
				} else {
					installation = new Installation(modVersion.modid, modVersion.version);
					installation.status = StatusInstallation.MANUELLE;
					installation.fichier = this.dossier.relativize(resultat.fichier.toPath()).toString();
					this.installations.put(installation.modid, installation);
				}
				installation.fichier = dossier.resolve("mods").relativize(Path.of(resultat.fichier.getAbsolutePath()))
						.toString();
			} catch (MalformedURLException mue) {
				System.err.println(String.format("[DepotInstallation] [ERROR] in '%s': %s", resultat.fichier.getName(),
						mue.getMessage()));
			}
		}
	}
	
	/**
	 * Retourne le status d'installation d'un mod.
	 * <p>
	 * Si aucune information n'est disponible, cette version est considérée comme installée manuellement. En mode
	 * manuel, les fichiers déjà présents peuvent être mise à jour mais pas supprimés.
	 */
	public StatusInstallation statusMod(ModVersion version) {
		if (this.installations.containsKey(version.modid)) return this.installations.get(version.modid).status;
		return StatusInstallation.MANUELLE;
	}
	
	/**
	 * Change le status associé à une version de mod.
	 */
	public void statusChange(ModVersion version, StatusInstallation status) {
		if (this.installations.containsKey(version.modid)) {
			this.installations.get(version.modid).status = status;
		} else {
			Installation i = new Installation(version);
			i.status = status;
			this.installations.put(i.modid, i);
		}
	}
	
	/**
	 * Efface le status associé à une version de mod.
	 */
	public void statusSuppression(ModVersion version) {
		this.installations.remove(version.modid);
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
				JSONTokener tokener = new JSONTokener(fis);
				JSONObject parser = new JSONObject(tokener);
				
				JSONObject MINECRAFT = parser.getJSONObject("minecraft");
				if (MINECRAFT != null) {
					String m = MINECRAFT.getString("version");
					if (m != null && !"null".equalsIgnoreCase(m)) this.mcversion = Version.read(m);
				}
				
				JSONObject MODS = parser.getJSONObject("mods");
				if (MODS != null) {
					for (String modid : MODS.keySet()) {
						JSONObject INFOS = MODS.getJSONObject(modid);
						Installation ins;
						// TODO: vérifier version
						if (this.installations.containsKey(modid)) ins = this.installations.get(modid);
						else {
							ins = new Installation(modid.intern(), Version.read(INFOS.getString("version")));
							this.installations.put(modid.intern(), ins);
						}
						
						ins.fichier = INFOS.getString("fichier");
						String mode = INFOS.getString("mode");
						if (mode.equalsIgnoreCase("auto")) ins.status = StatusInstallation.AUTO;
						else if (mode.equalsIgnoreCase("verrouille")) ins.status = StatusInstallation.VERROUILLE;
						else ins.status = StatusInstallation.MANUELLE;
					}
				}
			} catch (IOException io) {
				System.err.println(io.getClass() + ":" + io.getLocalizedMessage());
			}
		}
	}
	
	public void statusSauvegarde() {
		File infos = dossier.resolve("mods").resolve(".mods.json").toFile();
		try (FileOutputStream fos = new FileOutputStream(infos);
			 BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos))) {
			JSONObject data = new JSONObject();
			JSONObject minecraft = new JSONObject();
			minecraft.put("version", this.mcversion != null ? this.mcversion.toString() : "null");
			data.put("minecraft", minecraft);
			
			JSONObject MODS = new JSONObject();
			for (Installation i : this.installations.values()) {
				Map<String, Object> INFOS = new HashMap<>();
				INFOS.put("version", i.version.toString());
				INFOS.put("fichier", i.fichier);
				INFOS.put("mode", i.status.nom);
				MODS.put(i.modid, INFOS);
			}
			data.put("mods", MODS);
			data.write(bw, 4, 0);
		} catch (IOException io) {
			System.err.println(io.getClass() + ":" + io.getLocalizedMessage());
		}
	}
	
	@Override
	public void close() {
		this.statusSauvegarde();
	}
	
	/**
	 * Status d'une installation de mod.
	 */
	public enum StatusInstallation {
		/**
		 * Installation explicite par l'utilisateur.
		 */
		MANUELLE("manuel"),
		/**
		 * Installation automatique comme dépendance.
		 */
		AUTO("auto"),
		/**
		 * Installation vérouillée. Le mod ne peut pas être supprimé, même s'il génère des erreurs.
		 */
		VERROUILLE("verrouille");
		
		final String nom;
		
		StatusInstallation(String nom) {
			this.nom = nom;
		}
		
		@Override
		public String toString() {
			return this.nom;
		}
	}
	
	public static class Installation {
		public final String             modid;
		public final Version            version;
		public       StatusInstallation status = StatusInstallation.AUTO;
		public       String             fichier;
		
		public Installation(String modid, Version version) {
			this.modid = modid.intern();
			this.version = version;
		}
		
		public Installation(ModVersion modVersion) {
			this.modid = modVersion.modid;
			this.version = modVersion.version;
		}
	}
}
