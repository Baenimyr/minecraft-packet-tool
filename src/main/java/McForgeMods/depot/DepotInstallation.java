package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import McForgeMods.outils.NoNewlineReader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
public class DepotInstallation extends Depot {
	public static final Pattern                         minecraft_version   = Pattern.compile(
			"^(1\\.14(\\.[1-4])?|1\\.13(\\.[1-2])?|1\\.12(\\.[1-2])?|1\\.11(\\.[1-2])?|1\\.10(\\.[1-2])?|1\\.9(\\"
					+ ".[1-4])?|1\\.8(\\.1)?|1\\.7(\\.[1-9]|(10))?|1\\.6\\.[1-4]|1\\.5(\\.[1-2])?)-");
	public final        Path                            dossier;
	private final       Map<String, StatusInstallation> status_installation = new HashMap<>();
	
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
		if (this.contains(mversion.mod)) {
			this.mod_version.get(mversion.mod).remove(mversion);
			
			// Suppression des fichiers
			for (URL url : mversion.urls) {
				try {
					if (url.getProtocol().equals("file") && Path.of(url.toURI()).startsWith(this.dossier))
						Files.deleteIfExists(Path.of(url.toURI()));
				} catch (IOException | URISyntaxException ignored) {
				}
			}
			
			this.statusSuppression(mversion);
			this.statusSauvegarde();
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
		if (this.contains(statique.mod)) {
			List<ModVersion> perimes = this.getModVersions(statique.mod).stream()
					.filter(mv -> mv.mcversion.equals(statique.mcversion) && !mv.version.equals(statique.version))
					.collect(Collectors.toList());
			perimes.forEach(this::desinstallation);
		}
	}
	
	private static ModVersion lectureMcMod(InputStream lecture) {
		JSONTokener token = new JSONTokener(new NoNewlineReader(lecture));
		JSONObject json;
		JSONArray liste = new JSONArray(token);
		json = liste.getJSONObject(0);
		
		if (!json.has("modid") || !json.has("name")) return null;
		final String modid = json.getString("modid");
		final String name = json.getString("name");
		Version version, mcversion;
		
		String texte_version = json.getString("version");
		Matcher m = minecraft_version.matcher(texte_version);
		if (m.find()) {
			// System.out.println(String.format("[Version] '%s' => %s\t%s", texte_version, texte_version.substring(0, m.end() - 1), texte_version.substring(m.end())));
			mcversion = Version.read(texte_version.substring(0, m.end() - 1));
			version = Version.read(texte_version.substring(m.end()));
			
			if (json.has("mcversion")) mcversion = Version.read(json.getString("mcversion"));
		} else {
			version = Version.read(texte_version);
			mcversion = Version.read(json.getString("mcversion")); // obligatoire car non déduit de la version
		}
		
		final Mod mod = new Mod(modid, name);
		mod.description = json.has("description") ? json.getString("description") : null;
		mod.url = json.has("url") ? json.getString("url") : null;
		mod.updateJSON = json.has("updateJSON") ? json.getString("updateJSON") : null;
		
		if (mod.description != null && mod.description.length() == 0) mod.description = null;
		if (mod.url != null && mod.url.length() == 0) mod.url = null;
		if (mod.updateJSON != null && mod.updateJSON.length() == 0) mod.updateJSON = null;
		
		final ModVersion modVersion = new ModVersion(mod, version,
				new VersionIntervalle(mcversion, mcversion.precision()));
		
		if (json.has("requiredMods")) {
			VersionIntervalle.lectureDependances(json.getJSONArray("requiredMods"))
					.forEach(modVersion::ajoutModRequis);
		}
		if (json.has("dependencies")) {
			VersionIntervalle.lectureDependances(json.getJSONArray("dependencies"))
					.forEach(modVersion::ajoutModRequis);
		}
		if (json.has("dependants")) {
			JSONArray dependants = json.getJSONArray("dependants");
			dependants.forEach(d -> modVersion.ajoutDependant((String) d));
		}
		return modVersion;
	}
	
	/**
	 * Cette fonction lit un fichier et tente d'extraire les informations relatives au mod.
	 * <p>
	 * Un mod est un fichier jar contenant dans sa racine un fichier <b>mcmod.info</b>. Ce fichier contient une
	 * <b>liste</b> des mods que le fichier contient. Chaque mod définit un <i>modid</i>, un <i>name</i>, une
	 * <i>version</i> et une <i>mcversion</i>. Le format de la version doit être compatible avec le format définit par
	 * {@link Version}. La version minecraft peut être extraite de la <i>version</i> à la condition d'être sous le
	 * format "<i>mcversion</i>-<i>version</i>".
	 *
	 * @return {@code true} si réussite: il s'agit bien d'un mod Minecraft Forge
	 * @see <a href="https://mcforge.readthedocs.io/en/latest/gettingstarted/structuring/">Fichier mcmod.info</a>
	 */
	private boolean importationJar(File fichier) throws IOException {
		try (ZipFile zip = new ZipFile(fichier)) {
			ZipEntry mcmod = zip.getEntry("mcmod.info");
			if (mcmod == null) return false;
			BufferedInputStream lecture = new BufferedInputStream(zip.getInputStream(mcmod));
			
			ModVersion importe = lectureMcMod(lecture);
			if (importe != null) {
				final Mod mod = this.ajoutMod(importe.mod);
				final ModVersion modVersion = new ModVersion(mod, importe.version, importe.mcversion);
				modVersion.fusion(importe);
				
				this.ajoutModVersion(modVersion);
				modVersion.ajoutURL(fichier.getAbsoluteFile().toURI().toURL());
				modVersion.ajoutAlias(fichier.getName());
			}
		} catch (JSONException | IllegalArgumentException ignored) {
			// System.err.println("[DEBUG] [importation] '" + fichier.getName() + "':\t" + ignored.getMessage());
		}
		return false;
	}
	
	/**
	 * Parcours un dossier et les sous-dossiers à la recherche de fichier de mod forge.
	 * <p>
	 * Pour chaque fichier jar trouvé, tente d'importer les informations. Le moindre échec invalide l'importation.
	 *
	 * @param infos: un dépôt complet qui contient des informations supplémentaires
	 */
	public void analyseDossier(Depot infos) {
		Queue<File> dossiers = new LinkedList<>();
		dossiers.add(dossier.resolve("mods").toFile());
		
		while (!dossiers.isEmpty()) {
			File doss = dossiers.poll();
			File[] fichiers = doss.listFiles();
			if (fichiers != null) for (File f : fichiers) {
				if (f.isHidden()) ;
				else if (f.isDirectory() && !f.getName().equals("memory_repo") && !f.getName().equals("libraries"))
					dossiers.add(f);
				else if (f.getName().endsWith(".jar")) {
					try {
						boolean succes = importationJar(f);
						if (!succes && infos != null) {
							Optional<ModVersion> version_alias = infos.rechercheAlias(f.getName());
							if (version_alias.isPresent()) {
								// Ajout d'une version sans informations supplémentaires.
								ModVersion local = this.ajoutModVersion(
										new ModVersion(version_alias.get().mod, version_alias.get().version,
												version_alias.get().mcversion));
								local.fusion(version_alias.get()); // confiance d'avoir identifier le fichier
								local.ajoutURL(f.getAbsoluteFile().toURI().toURL());
								local.ajoutAlias(f.getName());
							}
						}
						// System.err.println("Fichier jar incompatible: " + f.getName());
					} catch (IOException i) {
						System.err.println("Erreur sur '" + f.getName() + "': " + i.getMessage());
					}
				}
			}
		}
		
		this.statusImportation();
	}
	
	/**
	 * Retourne le status d'installation d'un mod.
	 * <p>
	 * Si aucune information n'est disponible, cette version est considérée comme installée manuellement. En mode
	 * manuel, les fichiers déjà présents peuvent être mise à jour mais pas supprimés.
	 */
	public StatusInstallation statusMod(ModVersion version) {
		return this.status_installation
				.getOrDefault(version.mod.modid + " " + version.version, StatusInstallation.MANUELLE);
	}
	
	/** Change le status associé à une version de mod. */
	public void statusChange(ModVersion version, StatusInstallation status) {
		this.status_installation.put(version.mod.modid + " " + version.version, status);
	}
	
	/** Efface le status associé à une version de mod. */
	public void statusSuppression(ModVersion version) {
		this.status_installation.remove(version.mod.modid + " " + version.version);
	}
	
	/** Importe les informations sur le status d'installation.
	 *
	 * Tous les status sont importés même si les mods ont disparus. Un mod pourrait ne pas être détecté ou ce serait
	 * le résultat d'une mauvaise manipulation, la restauration de l'installation doit rester possible.
	 */
	public void statusImportation() {
		File infos = dossier.resolve("mods").resolve(".mods.txt").toFile();
		if (infos.exists()) {
			try (FileInputStream fis = new FileInputStream(infos);
				 BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
				String ligne;
				while ((ligne = br.readLine()) != null) {
					String[] args = ligne.split(" ");
					if (args.length == 3) {
						if (!this.contains(args[0])) {
							System.err.println(String.format("Mod '%s' disparu", args[0]));
						} else {
							Optional<ModVersion> mversion = this
									.getModVersion(this.getMod(args[0]), Version.read(args[1]));
							if (mversion.isEmpty()) {
								System.err.println(String.format("Mod '%s' %s disparu", args[0], args[1]));
							}
						}
						
						StatusInstallation status = StatusInstallation.MANUELLE;
						for (StatusInstallation s : StatusInstallation.values())
							if (s.nom.equalsIgnoreCase(args[2])) {
								status = s;
								break;
							}
						
						// Enregistre le status même si le fichier à disparu
						this.status_installation.put(args[0] + " " + args[1], status);
					}
				}
			} catch (IOException io) {
				System.err.println(io.getClass() + ":" + io.getLocalizedMessage());
			}
		}
	}
	
	public void statusSauvegarde() {
		File infos = dossier.resolve("mods").resolve(".mods.txt").toFile();
		try (FileOutputStream fos = new FileOutputStream(infos);
			 BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos))) {
			for (String mv : this.status_installation.keySet()) {
				bw.write(mv + " " + this.status_installation.get(mv).nom);
				bw.newLine();
			}
		} catch (IOException io) {
			System.err.println(io.getClass() + ":" + io.getLocalizedMessage());
		}
	}
	
	/** Status d'une installation de mod. */
	public enum StatusInstallation {
		/** Installation explicite par l'utilisateur. */
		MANUELLE("manuel"),
		/** Installation automatique comme dépendance. */
		AUTO("auto"),
		/** Installation vérouillée. Le mod ne peut pas être supprimé, même s'il génère des erreurs. */
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
}
