package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.Version;
import McForgeMods.outils.Dossiers;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * <h2>Format du dépot</h2>
 * - fichier Mods.json: index contenant la liste des mods disponibles et les informations générales écrites par la
 * fonction {@link Mod#json(JSONObject)}.
 * {
 * "<i>modid</i>": {
 * "name": "<i>name</i>",
 * "description": "<i>description</i>",
 * "url": "<i>URL</i>",
 * "updateJSON": "<i>URL</i>", ...
 * },...
 * }
 * - pour un modid <i>test</i>, le fichier <i>t/test/test.json</i> contient les informations relatives à chaque
 * version disponibles, écrites par la fonction {@link ModVersion#json(JSONObject)} sous le format:
 * {
 * <i>version</i>: {
 * "mcversion": "1.12.2",
 * "urls": [<i>liens de téléchargement</i>],
 * ...
 * }, ...
 * }
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
	public void importation() throws IOException {
		final File MODS = Dossiers.fichierIndexDepot(this.dossier).toFile();
		if (!MODS.exists()) {
			System.err.println("Absence du fichier principal: 'Mods.json'");
			return;
		}
		
		try (FileInputStream fichier = new FileInputStream(MODS)) {
			lectureFichierIndex(fichier);
		}
		for (String modid : getModids()) {
			this.importationMod(modid);
		}
		System.err.flush();
	}
	
	/**
	 * Analyse le fichier d'index pour importer les mods.
	 * L'index doit être un JSON dont les clés au premier niveau sont les modids.
	 * @param input contenu du fichier d'index.
	 */
	private void lectureFichierIndex(final InputStream input) {
		JSONTokener tokener = new JSONTokener(new BufferedInputStream(input));
		JSONObject json = new JSONObject(tokener);
		
		for (String modid : json.keySet()) {
			JSONObject data = json.getJSONObject(modid);
			Mod mod = new Mod(modid, data);
			this.ajoutMod(mod);
		}
	}
	
	private void importationMod(final String modid) {
		try (InputStream fichier = Dossiers.fichierModDepot(this.dossier.toUri().toURL(), modid).openStream()) {
			lectureFichierMod(modid, fichier);
		} catch (FileNotFoundException f) {
			System.err.println("Le fichier de données pour '" + modid + "' n'existe pas.");
		} catch (IOException | JSONException f) {
			System.err.println("Erreur de lecture des informations de '" + modid + "': " + f.getMessage());
		}
	}
	
	/**
	 * Analyse le fichier associé à un mod particulier pour extraire les informations de version.
	 * @param modid: identifiant du mod associé à ses informations.
	 * @param input  contenu du fichier
	 */
	private void lectureFichierMod(final String modid, final InputStream input) {
		JSONTokener tokener = new JSONTokener(input);
		JSONObject json = new JSONObject(tokener);
		
		for (String version : json.keySet()) {
			JSONObject v = json.getJSONObject(version);
			ModVersion modVersion = new ModVersion(this.getMod(modid), Version.read(version), v);
			this.ajoutModVersion(modVersion);
		}
	}
	
	/**
	 * Enregistre la liste des mods dans le fichier <i>Mods.json</i> à la racine du dépôt.
	 * Sauvegarde les informations d'un mod ({@link #sauvegardeMod(String)}) en même temps.
	 */
	public void sauvegarde() throws IOException {
		if (!this.dossier.toFile().exists()) this.dossier.toFile().mkdirs();
		
		try (FileOutputStream fichier = new FileOutputStream(Dossiers.fichierIndexDepot(this.dossier).toFile())) {
			OutputStreamWriter writer = new OutputStreamWriter(new BufferedOutputStream(fichier));
			
			JSONObject json = new JSONObject();
			List<String> modids = new ArrayList<>(mods.keySet());
			modids.sort(String::compareToIgnoreCase);
			
			for (String modid : modids) {
				JSONObject data = new JSONObject();
				Mod mod = this.mods.get(modid);
				mod.json(data);
				json.put(modid, data);
				
				this.sauvegardeMod(modid);
			}
			
			json.write(writer, 2, 0);
			writer.close();
		}
	}
	
	/**
	 * Enregistre les informations d'un mod.
	 * Enregistre toutes les versions disponibles ainsi que les informations de celles-ci.
	 * <p>
	 * Dans le dossier de dépôt, le fichier de sauvegarde se situe en <i>./m/modid/modid.json</i>
	 * @throws FileNotFoundException si impossible de créer le fichier de sauvegarde
	 */
	private void sauvegardeMod(String modid) throws IOException {
		final Mod mod = this.getMod(modid);
		if (!this.mod_version.containsKey(mod)) return;
		
		Path dossier_mod = Path.of(Dossiers.fichierModDepot(this.dossier.toUri().toURL(), modid).getPath());
		if (!dossier_mod.toFile().exists()) dossier_mod.toFile().mkdirs();
		
		try (FileOutputStream donnees = new FileOutputStream(dossier_mod.resolve(mod.modid + ".json").toFile())) {
			JSONObject json = new JSONObject();
			
			for (ModVersion mv : this.mod_version.get(mod)) {
				JSONObject json_version = new JSONObject();
				mv.json(json_version);
				json.put(mv.version.toString(), json_version);
			}
			
			OutputStreamWriter writer = new OutputStreamWriter(donnees);
			json.write(writer, 2, 0);
			writer.close();
		}
	}
	
	/**
	 * Télécharge les informations présente sur un dépot distant et actualise les informations actuelles.
	 * <p>
	 * Les nouvelles informations sont <i>fusionnées</i> avec les informations déjà présentes, même si les données
	 * présentes sont erronées. Il peut être conseillé de supprimer les données périmées avant d'activer la
	 * synchronisation avec {@link Depot#clear()}.
	 * @param depot_url : adresse à laquelle se trouve le dépot.
	 * @throws MalformedURLException si l'url n'est pas conpatible avec l'exploration d'arborescence de fichier
	 * @throws IOException           à la moindre erreur de lecture des flux réseau.
	 */
	void synchronisationDepot(URL depot_url) throws MalformedURLException, IOException {
		URL url_mods = Dossiers.fichierIndexDepot(depot_url);
		try (InputStream is = url_mods.openStream()) {
			this.lectureFichierIndex(new BufferedInputStream(is));
		}
		
		for (String modid : this.getModids()) {
			URL url_modid = Dossiers.fichierModDepot(depot_url, modid);
			try (InputStream is = url_modid.openStream()) {
				this.lectureFichierMod(modid, is);
			} catch (FileNotFoundException f) {
				System.err.println(
						String.format("Les données de version pour le mod '%s' ne sont pas disponibles.", modid));
			}
		}
	}
}
