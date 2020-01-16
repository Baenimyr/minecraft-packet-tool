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
import java.util.*;
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
	public static final Pattern                   minecraft_version = Pattern.compile(
			"^1\\.(14(\\.[1-4])?|13(\\.[1-2])?|12(\\.[1-2])?|11(\\.1)?|10(\\.[1-2])?|9(\\.[1-4])?|8(\\.1)?|7(\\.[1-9]|(10))?)-");
	public final        Path                      dossier;
	/**
	 * Fait la correspondance entre un fichier et une version de mod.
	 * Si la version est nulle, cela signifie que le fichier n'a pas pu être chargé, probablement parce qu'il ne
	 * contenait pas de fichier <i>mcmod.info</i>.
	 */
	public final        HashMap<File, ModVersion> correspondances   = new HashMap<>();
	
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
			
			ModVersion modVersion = new ModVersion(this.ajoutMod(mod), version, mcversion);
			modVersion.ajoutURL(fichier.getAbsoluteFile().toURI().toURL());
			
			if (json.has("requiredMods")) {
				lectureDependances(json.getJSONArray("requiredMods")).forEach(modVersion::ajoutModRequis);
			}
			if (json.has("dependencies")) {
				lectureDependances(json.getJSONArray("dependencies")).forEach(modVersion::ajoutModRequis);
			}
			if (json.has("dependants")) {
				JSONArray dependants = json.getJSONArray("dependants");
				dependants.forEach(d -> modVersion.ajoutDependant((String) d));
			}
			modVersion.ajoutAlias(fichier.getName());
			this.ajoutModVersion(modVersion);
			this.correspondances.put(fichier, modVersion);
			return true;
		} catch (JSONException | IllegalArgumentException ignored) {
			// System.err.println("[DEBUG] [importation] '" + fichier.getName() + "':\t" + ignored.getMessage());
		}
		return false;
	}
	
	static Map<String, VersionIntervalle> lectureDependances(Iterable<Object> entree) throws IllegalFormatException {
		final Map<String, VersionIntervalle> resultat = new HashMap<>();
		for (Object o : entree) {
			String texte = o.toString();
			int pos = 0;
			StringBuilder sb = new StringBuilder();
			VersionIntervalle versionIntervalle = null;
			
			while (pos < texte.length()) {
				char c = texte.charAt(pos);
				if (c == ',') {
					resultat.put(sb.toString().toLowerCase(),
							versionIntervalle == null ? new VersionIntervalle() : versionIntervalle);
					sb = new StringBuilder();
					versionIntervalle = null;
				} else if (c == '@' && sb.length() > 0) {
					StringBuilder dep = new StringBuilder();
					while (pos < texte.length() && (Character.isDigit(c) || c == ',' || c == '[' || c == ']' || c == '('
							|| c == ')' || c == '.')) {
						dep.append(c);
						c = texte.charAt(++pos);
					}
					versionIntervalle = VersionIntervalle.read(dep.toString());
					
				} else if (Character.isAlphabetic(c) || Character.isDigit(c)) {
					sb.append(c);
				} else {
					throw new IllegalArgumentException(texte);
				}
				pos++;
			}
			
			if (sb.length() > 0) {
				resultat.put(sb.toString().toLowerCase(),
						versionIntervalle == null ? new VersionIntervalle() : versionIntervalle);
			}
		}
		return resultat;
	}
	
	/**
	 * Parcours un dossier et les sous-dossiers à la recherche de fichier de mod forge.
	 * <p>
	 * Pour chaque fichier jar trouvé, tente d'importer les informations.
	 * Le moindre échec invalide l'importation.
	 */
	public void analyseDossier(Depot infos) {
		Queue<File> dossiers = new LinkedList<>();
		dossiers.add(dossier.resolve("mods").toFile().getAbsoluteFile());
		
		while (!dossiers.isEmpty()) {
			File doss = dossiers.poll();
			File[] fichiers = doss.listFiles();
			if (fichiers != null) for (File f : fichiers) {
				if (f.isHidden()) continue;
				else if (f.isDirectory() && !f.getName().equals("memory_repo")) dossiers.add(f);
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
								this.correspondances.put(f, local);
								continue;
							}
						}
						System.err.println("Fichier jar incompatible: " + f.getName());
					} catch (IOException i) {
						System.err.println("Erreur sur '" + f.getName() + "': " + i.getMessage());
					}
					this.correspondances.putIfAbsent(f, null);
				}
			}
		}
	}
}
