package McForgeMods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.*;

/**
 * Un paquet est une archive tar qui contient un fichier `mods.json` avec les informations et un dossier `files`
 * contenant tous les fichies devant être installés.
 * <p>
 * L'installation se fait par des paquets. Les paquets disposent d'un identifiant unique ({@link #modid}), généralement
 * celui du mod qu'ils installent et une version. Un paquet peut être dépendant d'autres paquets, ainsi les dépendances
 * seront installées automatiquement et cela permet de diviser un mod en librairies partagées.
 *
 * <h2><i>mods.json</i></h2>
 * { "name": MODID, "version": VERSION, "dependencies": [ <i>modid</i>@<i>versions</i>, <i>modid</i>@<i>versions</i> ],
 * "files": { "mods/<i>Mod-1.12.2-1.3.52.jar</i>": {} "librairies/<i>librairie</i>": {} } }
 *
 * <h2>Fichiers</h2>
 * Contenir des fichiers à installer est facultatif: un paquet peut être un moyen d'installer un lot de mods en une fois
 * (modpack). Tous les fichiers à installer doivent être déclarés dans le fichier d'information.
 *
 * <h3>Exemple de paquet</h3>
 * - mods.json - files - mods/BiomesOPlenty-1.12.2-9.0.jar - config/biomesoplenty.cfg
 */
public class PaquetMinecraft {
	/** Fichier d'information du paquet. */
	public static final String INFOS    = "mods.json";
	/** Dossier de l'archive contenant les fichiers à installer. */
	public static final String FICHIERS = "/";
	
	public final String                         modid;
	public final Version                        version;
	public final VersionIntervalle              mcversion;
	/** Liste des fichiers associés à l'installation. */
	public final List<FichierMetadata>          fichiers     = new ArrayList<>();
	/**
	 * Mods obligatoires pour le bon fonctionnement de celui-ci. Une intervalle de version doit être spécifiée ou nulle
	 * pour n'importe quelle version.
	 */
	public final Map<String, VersionIntervalle> requiredMods = new HashMap<>();
	/** Une description simple pouvant être affichée */
	public       String                         description  = null;
	
	public PaquetMinecraft(String modid, Version version, VersionIntervalle mcversion) {
		this.modid = modid.intern();
		this.version = version;
		this.mcversion = mcversion;
	}
	
	/**
	 * Ajoute une nouvelle dépendance.
	 * <p>
	 * Si un mod identique est déjà enregistrée, enregistre l'intersection entre les deux intervalles de version.
	 *
	 * @param modid requis
	 * @param intervalle pour laquelle le modid est requis.
	 */
	public void ajoutModRequis(String modid, VersionIntervalle intervalle) {
		modid = modid.toLowerCase().intern();
		if (this.requiredMods.containsKey(modid) && this.requiredMods.get(modid) != null) this.requiredMods.get(modid).intersection(intervalle);
		else this.requiredMods.put(modid, intervalle);
	}
	
	/** Lit les informations relatives à un paquet. */
	public static PaquetMinecraft lecturePaquet(JSONObject json) {
		PaquetMinecraft modVersion = new PaquetMinecraft(json.getString("name"),
				Version.read(json.getString("version")), VersionIntervalle.read(json.getString("mcversion")));
		modVersion.description = json.optString("description", null);
		
		if (json.has("dependencies")) {
			JSONArray depen = json.getJSONArray("dependencies");
			Map<String, VersionIntervalle> dependences = VersionIntervalle.lectureDependances(depen);
			dependences.forEach(modVersion::ajoutModRequis);
		}
		
		if (json.has("files")) {
			JSONObject files = json.getJSONObject("files");
			for (String nom : files.keySet()) {
				FichierMetadata fichier = new FichierMetadata(Path.of(nom));
				modVersion.fichiers.add(fichier);
			}
		}
		
		return modVersion;
	}
	
	/**
	 * Importe les nouvelles données à partir d'une instance similaire.
	 */
	public void fusion(PaquetMinecraft autre) {
		if (this.description == null) this.description = autre.description;
		autre.requiredMods.forEach(this::ajoutModRequis);
		autre.fichiers.stream().filter(f -> this.fichiers.stream().noneMatch(f2 -> f.path == f2.path))
				.forEach(this.fichiers::add);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(modid, version);
	}
	
	@Override
	public String toString() {
		return String.format("'%s' %s", modid, version);
	}
	
	public String toStringStandard() {
		return String.format("%s-%s", modid, version);
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PaquetMinecraft that = (PaquetMinecraft) o;
		return modid.equals(that.modid) && version.equals(that.version);
	}
	
	/** Enregistre toutes les informations du mod dans l'objet json. */
	public void ecriturePaquet(JSONObject json) {
		json.put("name", this.modid);
		json.put("version", this.version);
		json.put("mcversion", this.mcversion);
		if (this.description != null) json.put("description", this.description);
		
		JSONArray dependencies = new JSONArray();
		for (String modid : this.requiredMods.keySet()) {
			dependencies.put(modid + "@" + this.requiredMods.get(modid).toString());
		}
		
		JSONObject files = new JSONObject();
		for (FichierMetadata fichier : this.fichiers) {
			JSONObject fichier_metadata = new JSONObject();
			
			files.put(fichier.path.toString(), fichier_metadata);
		}
		
		json.put("dependencies", dependencies);
		json.put("files", files);
	}
	
	/**
	 * Un fichier qui sera installé.
	 * <p>
	 * Cette classe indique où sera installé le fichier grâce à son nom relatif à la racine du dossier minecraft (ex:
	 * mods/Mod.jar). Des informations de contrôle, comme des somme de hashage permettent de vérifier l'intégrité du
	 * fichier.
	 */
	public static class FichierMetadata {
		public final String path;
		public       String SHA256 = null;
		
		public FichierMetadata(String path) {
			this.path = path;
		}
		
		public FichierMetadata(Path path) {
			this(path.toString());
		}
	}
}