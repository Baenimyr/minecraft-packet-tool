package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import McForgeMods.outils.Dossiers;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

/**
 * <h2>Format du dépot</h2>
 * <ul>
 * <li>
 * fichier Mods.json: index contenant la liste des mods disponibles et les informations générales écrites par la
 * fonction {@link #sauvegarde()}.
 * {
 *     "<i>modid</i>": {
 *         "name": "<i>name</i>",
 *         "description": "<i>description</i>",
 *         "url": "<i>URL</i>",
 *         "updateJSON": "<i>URL</i>",
 *         ... },
 *     ... }
 * </li>
 * <li>
 * pour un modid <i>test</i>, le fichier
 * <i>t/test/test.json</i> contient les informations relatives à chaque version disponibles, écrites par la fonction
 * {@link #ecritureFichierMod(String, OutputStream)} sous le format:
 * {
 *     <i>version</i>: {
 *         "mcversion": "1.12.2",
 *         "urls": [<i>liens de téléchargement</i>],
 *         ... },
 *     ... }
 * </li>
 * </ul>
 *
 * @see Mod
 * @see ModVersion
 */
public class DepotLocal extends Depot {
	
	public final Path dossier;
	
	public DepotLocal(Path dossier) {
		this.dossier = Dossiers.dossierDepot(dossier);
	}
	
	/**
	 * Importe les informations du dépot à partir du répertoire de sauvegarde.
	 */
	public void importation() throws IOException, JSONException {
		final File MODS = Dossiers.fichierIndexDepot(this.dossier).toFile();
		if (!MODS.exists()) {
			System.err.println("Absence du fichier principal: 'Mods.json'");
			return;
		}
		
		try (FileInputStream fichier = new FileInputStream(MODS)) {
			lectureFichierIndex(fichier);
		}
		
		ArrayList<String> modids = new ArrayList<>(getModids());
		Collections.sort(modids);
		for (String modid : modids) {
			final File nom_fichier = Dossiers.fichierModDepot(this.dossier, modid).toFile();
			if (!nom_fichier.exists()) System.err.println("Le fichier de données pour '" + modid + "' n'existe pas.");
			
			try (FileInputStream fichier = new FileInputStream(nom_fichier)) {
				BufferedInputStream buff = new BufferedInputStream(fichier);
				if (buff.available() == 0) return;
				lectureFichierMod(modid, buff);
			}
		}
		System.err.flush();
	}
	
	/**
	 * Analyse le fichier d'index pour importer les mods. L'index doit être un JSON dont les clés au premier niveau sont
	 * les modids.
	 *
	 * @param input contenu du fichier d'index.
	 */
	private void lectureFichierIndex(final InputStream input) throws JSONException {
		JSONTokener tokener = new JSONTokener(new BufferedInputStream(input));
		JSONObject json = new JSONObject(tokener);
		
		for (String modid : json.keySet()) {
			JSONObject data = json.getJSONObject(modid);
			Mod mod = this.ajoutMod(new Mod(modid, data.getString("name")));
			if (data.has("url")) {
				String url = data.getString("url");
				mod.url = url.length() == 0 ? null : url;
			}
			if (data.has("description")) {
				String description = data.getString("description");
				mod.description = description.length() == 0 ? null : description;
			}
			if (data.has("updateJSON")) {
				String updateJSON = data.getString("updateJSON");
				mod.updateJSON = updateJSON.length() == 0 ? null : updateJSON;
			}
			this.ajoutMod(mod);
		}
	}
	
	/**
	 * Analyse le fichier associé à un mod particulier pour extraire les informations de version.
	 *
	 * @param modid: identifiant du mod associé à ses informations.
	 * @param input  contenu du fichier
	 */
	private void lectureFichierMod(final String modid, final InputStream input) {
		JSONTokener tokener = new JSONTokener(input);
		JSONObject json_total = new JSONObject(tokener);
		
		for (String version : json_total.keySet()) {
			JSONObject json = json_total.getJSONObject(version);
			final ModVersion mv = this.ajoutModVersion(new ModVersion(this.getMod(modid), Version.read(version),
					Version.read(json.getString("mcversion"))));
			
			if (json.has("urls")) {
				Object url = json.get("urls");
				if (url instanceof JSONArray) {
					for (int i = 0; i < ((JSONArray) url).length(); i++)
						try {
							mv.ajoutURL(new URL(this.dossier.toUri().toURL(), ((JSONArray) url).getString(i)));
						} catch (MalformedURLException u) {
							u.printStackTrace();
						}
				} else {
					try {
						mv.ajoutURL(new URL(json.getString("urls")));
					} catch (MalformedURLException u) {
						u.printStackTrace();
					}
				}
			}
			
			if (json.has("requiredMods")) {
				JSONArray liste = json.getJSONArray("requiredMods");
				for (Map.Entry<String, VersionIntervalle> dependances : VersionIntervalle.lectureDependances(liste)
						.entrySet())
					mv.ajoutModRequis(dependances.getKey(), dependances.getValue());
			}
			
			if (json.has("dependants")) {
				JSONArray liste = json.getJSONArray("dependants");
				for (int i = 0; i < liste.length(); i++) {
					mv.ajoutDependant(liste.getString(i));
				}
			}
			
			if (json.has("alias")) {
				JSONArray liste = json.getJSONArray("alias");
				for (int i = 0; i < liste.length(); i++)
					mv.ajoutAlias(liste.getString(i));
			}
		}
	}
	
	/**
	 * Enregistre la liste des mods dans le fichier <i>Mods.json</i> à la racine du dépôt. Sauvegarde les informations
	 * d'un mod ({@link #ecritureFichierMod(String, OutputStream)}) en même temps.
	 */
	public void sauvegarde() throws IOException {
		if (!this.dossier.toFile().exists() && !this.dossier.toFile().mkdirs()) return;
		
		try (FileOutputStream fichier = new FileOutputStream(Dossiers.fichierIndexDepot(this.dossier).toFile());
			 BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fichier)) {
			ecritureFichierIndex(bufferedOutputStream);
		}
		
		ArrayList<String> liste = new ArrayList<>(this.getModids());
		Collections.sort(liste);
		for (String modid : liste) {
			Path fichier_mod = Dossiers.fichierModDepot(this.dossier, modid);
			if (!fichier_mod.getParent().toFile().exists()) fichier_mod.getParent().toFile().mkdirs();
			try (FileOutputStream donnees = new FileOutputStream(fichier_mod.toFile());
				 BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(donnees)) {
				this.ecritureFichierMod(modid, bufferedOutputStream);
			} catch (FileNotFoundException f) {
				System.err.println(String.format("Impossible d'écrire le fichier '%s'.", fichier_mod.toString()));
			}
		}
	}
	
	/**
	 * Écrit le fichier d'index du dépot sur le flux.
	 * <p>
	 * Le fichier d'index recense tous les modids et les informations générales à chaque mod.
	 *
	 * @param outputStream: flux d'écriture.
	 */
	private void ecritureFichierIndex(final OutputStream outputStream) throws IOException {
		JSONObject json = new JSONObject();
		List<String> modids = new ArrayList<>(mods.keySet());
		modids.sort(String::compareToIgnoreCase);
		
		for (String modid : modids) {
			final JSONObject data = new JSONObject();
			final Mod mod = this.mods.get(modid);
			data.put("name", mod.name);
			if (mod.url != null && mod.url.length() > 0) data.put("url", mod.url);
			if (mod.description != null && mod.description.length() > 0) data.put("description", mod.description);
			if (mod.updateJSON != null && mod.updateJSON.length() > 0) data.put("updateJSON", mod.updateJSON);
			json.put(modid, data);
		}
		
		try (OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
			json.write(writer, 2, 0);
		}
	}
	
	/**
	 * Enregistre les informations d'un mod. Enregistre toutes les versions disponibles ainsi que les informations de
	 * celles-ci.
	 * <p>
	 * Dans le dossier de dépôt, le fichier de sauvegarde se situe en <i>./m/modid/modid.json</i>
	 */
	private void ecritureFichierMod(String modid, final OutputStream outputStream) throws IOException {
		final Mod mod = this.getMod(modid);
		final JSONObject json_total = new JSONObject();
		
		for (ModVersion mv : this.mod_version.get(mod)) {
			JSONObject json = new JSONObject();
			mv.urls.sort(Comparator.comparing(URL::toString));
			mv.dependants.sort(String::compareTo);
			mv.alias.sort(String::compareTo);
			
			json.put("mcversion", mv.mcversion);
			
			JSONArray urls = new JSONArray();
			for (URL url : mv.urls) {
				if (url.getProtocol().equals("file") && url.getHost().isEmpty()) {
					Path path = Path.of(url.getPath());
					if (path.startsWith(this.dossier)) urls.put(this.dossier.relativize(path));
					else urls.put(url.toString());
				} else urls.put(url.toString());
			}
			json.put("urls", urls);
			
			JSONArray liste = new JSONArray();
			mv.requiredMods.entrySet().stream().sorted(Map.Entry.comparingByKey())
					.map(e -> e.getValue() != null ? e.getKey() + "@" + e.getValue() : e.getKey()).forEach(liste::put);
			json.put("requiredMods", liste);
			
			json.put("dependants", new JSONArray(mv.dependants));
			json.put("alias", new JSONArray(mv.alias));
			json_total.put(mv.version.toString(), json);
		}
		
		try (OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
			json_total.write(writer, 2, 0);
		}
	}
	
	/**
	 * Télécharge les informations présente sur un dépot distant et actualise les informations actuelles.
	 * <p>
	 * Les nouvelles informations sont <i>fusionnées</i> avec les informations déjà présentes, même si les données
	 * présentes sont erronées. Il peut être conseillé de supprimer les données périmées avant d'activer la
	 * synchronisation avec {@link Depot#clear()}.
	 *
	 * @param depot_distant: interface du dépôt distant
	 * @throws MalformedURLException si l'url n'est pas conpatible avec l'exploration d'arborescence de fichier
	 * @throws IOException           à la moindre erreur de lecture des flux réseau.
	 */
	public void synchronisationDepot(DepotDistant depot_distant) throws MalformedURLException, IOException {
		try (InputStream is = depot_distant.fichierIndexDepot()) {
			this.lectureFichierIndex(new BufferedInputStream(is));
		}
		
		final List<String> modids = new ArrayList<>(this.getModids());
		for (int i = 0; i < modids.size(); i++) {
			String modid = modids.get(i);
			try (InputStream is = depot_distant.fichierModDepot(modid)) {
				this.lectureFichierMod(modid, is);
				float progres = (float) i / modids.size();
				System.out.print(String.format("\r[%s>%s] %d/%d     ", "=".repeat((int) (50. * progres)),
						" ".repeat((int) (50 * (1. - progres) - 1)), (i + 1), modids.size()));
			} catch (FileNotFoundException f) {
				System.err.println(
						String.format("Les données de version pour le mod '%s' ne sont pas disponibles.", modid));
			}
		}
		System.out.println();
		System.out.println(String.format("%d mods récupérés.", modids.size()));
	}
}
