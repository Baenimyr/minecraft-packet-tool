package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import McForgeMods.outils.Dossiers;
import McForgeMods.outils.NoNewlineReader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Ce dépot lit les informations directement dans les fichiers qu'il rencontre.
 * Il permet d'analyser une instance minecraft (un dossier .minecraft) pour déduire les mods présents.
 * Ensuite toutes les actions d'un dépot sont applicables: présence de mod, présence de version, ...
 * <p>
 * L'unique contrainte avec un dépot d'installation, est qu'il ne peut pas y avoir plus d'un seul fichier par modid
 * et version minecraft dans les dossiers <i>mods</i> ou <i>mods/mcversion</i>. Autrement, il y a conflit lors du
 * chargement du jeu. Seulement si les versions de mod sont différentes, le dépot d'installation sera capable de
 * détecter cette erreur.
 */
public class DepotInstallation extends Depot {
	public static final Pattern minecraft_version = Pattern.compile(
			"^(1\\.14(\\.[1-4])?|1\\.13(\\.[1-2])?|1\\.12(\\.[1-2])?|1\\.11(\\.[1-2])?|1\\.10(\\.[1-2])?|1\\.9(\\"
					+ ".[1-4])?|1\\.8(\\.1)?|1\\.7(\\.[1-9]|(10))?|1\\.6\\.[1-4]|1\\.5(\\.[1-2])?)-");
	public final        Path    dossier;
	
	public DepotInstallation(Path dossier) {
		this.dossier = Dossiers.dossierMinecraft(dossier);
	}
	
	/**
	 * Cette fonction lit un fichier et tente d'extraire les informations relatives au mod.
	 * <p>
	 * Un mod est un fichier jar contenant dans sa racine un fichier <b>mcmod.info</b>.
	 * Ce fichier contient une <b>liste</b> des mods que le fichier contient.
	 * Chaque mod définit un <i>modid</i>, un <i>name</i>, une <i>version</i> et une <i>mcversion</i>.
	 * Le format de la version doit être compatible avec le format définit par {@link Version}.
	 * La version minecraft peut être extraite de la <i>version</i> à la condition d'être sous le format "<i>mcversion</i>-<i>version</i>".
	 * @return {@code true} si réussite: il s'agit bien d'un mod Minecraft Forge
	 * @see <a href="https://mcforge.readthedocs.io/en/latest/gettingstarted/structuring/">Fichier mcmod.info</a>
	 */
	public boolean importationJar(File fichier) throws IOException {
		try (ZipFile zip = new ZipFile(fichier)) {
			ZipEntry mcmod = zip.getEntry("mcmod.info");
			if (mcmod == null) return false;
			BufferedInputStream lecture = new BufferedInputStream(zip.getInputStream(mcmod));
			
			JSONTokener token = new JSONTokener(new NoNewlineReader(lecture));
			JSONObject json;
			JSONArray liste = new JSONArray(token);
			json = liste.getJSONObject(0);
			
			if (!json.has("modid") || !json.has("name")) return false;
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
			
			final ModVersion modVersion = this.ajoutModVersion(new ModVersion(this.ajoutMod(mod), version, mcversion));
			modVersion.ajoutURL(fichier.getAbsoluteFile().toURI().toURL());
			
			if (json.has("requiredMods")) {
				VersionIntervalle.lectureDependances(json.getJSONArray("requiredMods")).forEach(modVersion::ajoutModRequis);
			}
			if (json.has("dependencies")) {
				VersionIntervalle.lectureDependances(json.getJSONArray("dependencies")).forEach(modVersion::ajoutModRequis);
			}
			if (json.has("dependants")) {
				JSONArray dependants = json.getJSONArray("dependants");
				dependants.forEach(d -> modVersion.ajoutDependant((String) d));
			}
			modVersion.ajoutAlias(fichier.getName());
			return true;
		} catch (JSONException | IllegalArgumentException ignored) {
			// System.err.println("[DEBUG] [importation] '" + fichier.getName() + "':\t" + ignored.getMessage());
		}
		return false;
	}
	
	/**
	 * Parcours un dossier et les sous-dossiers à la recherche de fichier de mod forge.
	 * <p>
	 * Pour chaque fichier jar trouvé, tente d'importer les informations.
	 * Le moindre échec invalide l'importation.
	 */
	public void analyseDossier(Depot infos) {
		Queue<File> dossiers = new LinkedList<>();
		dossiers.add(dossier.toFile().getAbsoluteFile());
		
		while (!dossiers.isEmpty()) {
			File doss = dossiers.poll();
			File[] fichiers = doss.listFiles();
			if (fichiers != null) for (File f : fichiers) {
				if (f.isHidden());
				else if (f.isDirectory() && !f.getName().equals("memory_repo") && !f.getName().equals("libraries")) dossiers.add(f);
				else if (f.getName().endsWith(".jar")) {
					try {
						boolean succes = importationJar(f);
						if (succes) continue;
						if (infos != null) {
							Optional<ModVersion> version_alias = infos.rechercheAlias(f.getName());
							if (version_alias.isPresent()) {
								// Ajout d'une version sans informations supplémentaires.
								ModVersion local = this.ajoutModVersion(
										new ModVersion(version_alias.get().mod, version_alias.get().version,
												version_alias.get().mcversion));
								local.fusion(version_alias.get()); // confiance d'avoir identifier le fichier
								local.ajoutURL(f.getAbsoluteFile().toURI().toURL());
							}
						}
						// System.err.println("Fichier jar incompatible: " + f.getName());
					} catch (IOException i) {
						System.err.println("Erreur sur '" + f.getName() + "': " + i.getMessage());
					}
				}
			}
		}
	}
}
