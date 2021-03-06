package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.PaquetMinecraft;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import McForgeMods.outils.NoNewlineReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Les informations récupérées dans une archive de mod correspondent à un mod et à une version de ce mod. Les
 * informations relatives au mod (url, auteurs) sont censées etre universelles mais peuvent varier d'un fichier à
 * l'autre, c'est pourquoi l'instance de {@link Mod} créée pour transporter ces informations ne doit pas etre mélangée
 * avec celle utilisée par un dépot. Cette classe réunis les informations extraites telle quelle de l'archive jar.
 */
public class ArchiveMod {
	public static final Pattern         minecraft_version = Pattern.compile(
			"^(1\\.14(\\.[1-4])?|1\\.13(\\.[1-2])?|1\\.12(\\.[1-2])?|1\\.11(\\.[1-2])?|1\\.10(\\.[1-2])?|1\\.9(\\"
					+ ".[1-4])?|1\\.8(\\.1)?|1\\.7(\\.[1-9]|(10))?|1\\.6\\.[1-4]|1\\.5(\\.[1-2])?)-");
	public              File            fichier;
	public              PaquetMinecraft modVersion        = null;
	
	/**
	 * Cette fonction lit un fichier et tente d'extraire les informations relatives au mod.
	 * <p>
	 * Un mod est un fichier jar contenant un fichier <b>mcmod.info</b> avant 1.14.4 et un fichier META-INF/mcmod .toml
	 * à partir de minecraft 1.14.4. Ce fichier contient une <b>liste</b> des mods que le fichier contient. Chaque mod
	 * définit un <i>modid</i>, un <i>name</i>, une
	 * <i>version</i> et une <i>mcversion</i>. Le format de la version doit être compatible avec le format définit par
	 * {@link Version}. La version minecraft peut être extraite de la <i>version</i> à la condition d'être sous le
	 * format "<i>mcversion</i>-<i>version</i>".
	 *
	 * @return un {@link ArchiveMod} non vide si réussi: il s'agit bien d'un mod Minecraft Forge
	 * @see <a href="https://mcforge.readthedocs.io/en/latest/gettingstarted/structuring/">Fichier mcmod.info</a>
	 */
	public static ArchiveMod importationJar(File fichier) throws IOException {
		ArchiveMod archive = new ArchiveMod();
		try (ZipFile zip = new ZipFile(fichier)) {
			ZipEntry modToml = zip.getEntry("META-INF/mods.toml");
			if (modToml != null) {
				archive.lectureModTOML(zip.getInputStream(modToml));
			}
			
			if (!archive.isPresent()) {
				ZipEntry mcmod = zip.getEntry("mcmod.info");
				if (mcmod != null)
					try (BufferedInputStream lecture = new BufferedInputStream(zip.getInputStream(mcmod))) {
						archive.lectureMcMod(lecture);
					}
			}
		}
		return archive;
	}
	
	private void lectureMcMod(InputStream lecture) {
		JSONTokener token = new JSONTokener(new NoNewlineReader(lecture));
		JSONObject json;
		JSONArray liste = new JSONArray(token);
		json = liste.getJSONObject(0);
		
		if (!json.has("modid") || !json.has("name")) return;
		final String modid = json.getString("modid");
		final String name = json.getString("name");
		Version version;
		VersionIntervalle mcversion;
		
		String texte_version = json.getString("version");
		Matcher m = minecraft_version.matcher(texte_version);
		if (m.find()) {
			// System.out.println(String.format("[Version] '%s' => %s\t%s", texte_version, texte_version.substring(0, m.end() - 1), texte_version.substring(m.end())));
			mcversion = VersionIntervalle.read(texte_version.substring(0, m.end() - 1));
			version = Version.read(texte_version.substring(m.end()));
			
			if (json.has("mcversion")) mcversion = VersionIntervalle.read(json.getString("mcversion"));
		} else {
			version = Version.read(texte_version);
			mcversion = VersionIntervalle.read(json.getString("mcversion")); // obligatoire car non déduit de la version
		}
		
		// archive.mod = new Mod(modid);
		// archive.mod.name = name;
		String description = json.has("description") ? json.getString("description") : null;
		// archive.mod.url = json.has("url") ? json.getString("url") : null;
		// archive.mod.updateJSON = json.has("updateJSON") ? json.getString("updateJSON") : null;
		
		if (description != null && description.length() == 0) description = null;
		// if (archive.mod.url != null && archive.mod.url.length() == 0) archive.mod.url = null;
		// if (archive.mod.updateJSON != null && archive.mod.updateJSON.length() == 0) archive.mod.updateJSON = null;
		
		this.modVersion = new PaquetMinecraft(modid, version);
		this.modVersion.nomCommun = name;
		this.modVersion.description = description;
		this.modVersion.ajoutModRequis("minecraft", mcversion);
		if (json.has("requiredMods")) {
			VersionIntervalle.lectureDependances(json.getJSONArray("requiredMods"))
					.forEach(this.modVersion::ajoutModRequis);
		}
		if (json.has("dependencies")) {
			VersionIntervalle.lectureDependances(json.getJSONArray("dependencies"))
					.forEach(this.modVersion::ajoutModRequis);
		}
	}
	
	/**
	 * Parcours un dossier et les sous-dossiers à la recherche de fichier de mod forge.
	 * <p>
	 * Pour chaque fichier jar trouvé, tente d'importer les informations. Le moindre échec invalide l'importation.
	 *
	 * @param dossier: dossier système à parcourir
	 * @return la liste des archives détectées.
	 */
	public static List<ArchiveMod> analyseDossier(Path dossier) {
		final List<ArchiveMod> resultats = new LinkedList<>();
		Queue<File> dossiers = new LinkedList<>();
		dossiers.add(dossier.toFile());
		
		while (!dossiers.isEmpty()) {
			File doss = dossiers.poll();
			File[] fichiers = doss.listFiles();
			if (fichiers != null) for (File f : fichiers) {
				if (f.isHidden()) ;
				else if (f.isDirectory() && !f.getName().equals("memory_repo") && !f.getName().equals("libraries"))
					dossiers.add(f);
				else if (f.getName().endsWith(".jar")) {
					ArchiveMod resultat = null;
					try {
						resultat = ArchiveMod.importationJar(f);
					} catch (Exception i) {
						System.err.printf("[ERROR] in '%s': %s:%s%n", f, i.getClass().getSimpleName(), i.getMessage());
					}
					
					if (resultat != null && resultat.isPresent()) {
						resultat.fichier = f;
						resultats.add(resultat);
					}
				}
			}
		}
		return resultats;
	}
	
	private void lectureModTOML(InputStream lecture) throws IOException {
		TomlParseResult toml = Toml.parse(lecture);
		TomlArray mods = toml.getArray("mods");
		if (mods == null || mods.isEmpty()) {
			throw new JarModError("Aucun mod déclaré");
		}
		
		TomlTable mod_info = mods.getTable(0);
		if (mod_info == null || !mod_info.contains("modId")) {
			throw new JarModError("Aucun modId");
		}
		final String modid = mod_info.getString("modId");
		final Version version = Version.read(mod_info.getString("version"));
		String name = mod_info.getString("displayName");
		// archive.mod.url = mod_info.contains("displayURL") ? mod_info.getString("displayURL") : null;
		// archive.mod.updateJSON = mod_info.contains("updateJSONURL") ? mod_info.getString("updateJSONURL") : null;
		String description = mod_info.contains("description") ? mod_info.getString("description") : null;
		
		
		TomlArray dependencies_info = toml.getArray("dependencies." + modid);
		final Map<String, VersionIntervalle> dependencies = new HashMap<>();
		if (dependencies_info != null) for (int i = 0; i < dependencies_info.size(); i++) {
			TomlTable dep_i = dependencies_info.getTable(i);
			final String dep_modid = dep_i.getString("modId");
			final VersionIntervalle dep_versions = VersionIntervalle.read(dep_i.getString("versionRange"));
			
			dependencies.put(dep_modid, dep_versions);
		}
		
		this.modVersion = new PaquetMinecraft(modid, version);
		this.modVersion.requiredMods.putAll(dependencies);
		this.modVersion.nomCommun = name;
		this.modVersion.description = description;
	}
	
	public boolean isPresent() {
		return this.modVersion != null;
	}
	
	static class JarModError extends RuntimeException {
		
		public JarModError(String message) {
			super(message);
		}
		
		public JarModError(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
