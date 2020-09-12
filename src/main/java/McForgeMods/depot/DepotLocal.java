package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.PaquetMinecraft;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @see Mod
 * @see PaquetMinecraft
 */
public class DepotLocal extends Depot {
	public final Path                                                  dossier;
	public final Map<PaquetMinecraft, PaquetMinecraft.FichierMetadata> archives = new HashMap<>();
	
	/**
	 * Ouvre un dépôt local. Si aucun chemin n'est spécifié, l'emplacement par défaut est ~/.minecraft/forgemods
	 *
	 * @param dossier du dépôt ou {@code null}
	 */
	public DepotLocal(Path dossier) {
		if (dossier == null) {
			this.dossier = Path.of(System.getProperty("user.home")).resolve(".minecraft").resolve("forgemods");
		} else if (dossier.startsWith("~")) {
			this.dossier = Path.of(System.getProperty("user.home")).resolve(dossier.subpath(1, dossier.getNameCount()));
		} else {
			this.dossier = dossier.toAbsolutePath();
		}
	}
	
	/**
	 * Fournit un dossier de mod en cache. Les fichiers de mods peuvent être sauvegardés pour être installé sans
	 * téléchargement.
	 *
	 * @param modVersion: version à sauvegarder. Permet de séparer les éléments du cache.
	 * @return le chemin vers le dossier, ne donne pas de nom au fichier
	 */
	public Path dossierCache(PaquetMinecraft modVersion) {
		return dossier.resolve("cache").resolve(modVersion.modid);
	}
	
	private File fichierIndexDepot() {
		return dossier.resolve("Mods.json").toFile();
	}
	
	/**
	 * Analyse le fichier d'index pour importer les mods. L'index doit être un JSON dont les clés au premier niveau sont
	 * les modids.
	 *
	 * @param input contenu du fichier d'index.
	 */
	private void lectureFichierIndex(final InputStream input) throws JSONException {
		JSONTokener tokener = new JSONTokener(new BufferedInputStream(input));
		JSONArray mods = new JSONArray(tokener);
		
		for (int i = 0; i < mods.length(); i++) {
			JSONObject data = mods.getJSONObject(i);
			PaquetMinecraft modVersion = PaquetMinecraft.lecturePaquet(data);
			PaquetMinecraft.FichierMetadata paquet = new PaquetMinecraft.FichierMetadata(data.getString("filename"));
			if (data.has("sha256")) paquet.SHA256 = data.getString("sha256");
			
			this.ajoutModVersion(modVersion);
			this.archives.put(modVersion, paquet);
		}
	}
	
	/**
	 * Importe les informations du dépot à partir du répertoire de sauvegarde.
	 */
	public void importation() throws IOException, JSONException {
		final File MODS = fichierIndexDepot();
		if (!MODS.exists()) {
			System.err.println("Absence du fichier principal: 'Mods.json'");
			return;
		}
		
		try (FileInputStream fichier = new FileInputStream(MODS)) {
			lectureFichierIndex(fichier);
		} catch (JSONException je) {
			throw new JSONException("Erreur lecture fichier 'Mods.json'", je);
		}
	}
	
	/**
	 * Enregistre la liste des mods dans le fichier <i>Mods.json</i> à la racine du dépôt. Sauvegarde une partie des
	 * informations de {@link PaquetMinecraft} pour un aperçu rapide.
	 */
	public void sauvegarde() throws IOException {
		if (!this.dossier.toFile().exists() && !this.dossier.toFile().mkdirs()) return;
		
		try (FileOutputStream fichier = new FileOutputStream(fichierIndexDepot());
			 BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fichier)) {
			ecritureFichierIndex(bufferedOutputStream);
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
		JSONArray mods = new JSONArray();
		
		for (String modid : this.getModids())
			for (PaquetMinecraft modVersion : this.getModVersions(modid)) {
				final JSONObject data = new JSONObject();
				modVersion.ecriturePaquet(data);
				PaquetMinecraft.FichierMetadata archive = this.archives.get(modVersion);
				data.put("filename", archive.path.toString());
				if (archive.SHA256 != null) {
					data.put("sha256", archive.SHA256);
				}
				
				mods.put(data);
			}
		
		try (OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
			simpleJson.JSONWriter jwriter = new simpleJson.JSONWriter();
			jwriter.write(mods, writer);
		}
	}
	
	/**
	 * Efface toute information non essentielle du dépôt.
	 * <p>
	 * Si des versions de mods sont sauvegardées localement, une minimum d'information est conservé.
	 */
	public void clear() {
		this.mod_version.clear();
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
	 * @throws IOException à la moindre erreur de lecture des flux réseau.
	 */
	public void synchronisationDepot(DepotDistant depot_distant) throws MalformedURLException, IOException {
		try (InputStream is = depot_distant.fichierIndexDepot()) {
			this.lectureFichierIndex(new BufferedInputStream(is));
		}
		System.out.println();
	}
}
