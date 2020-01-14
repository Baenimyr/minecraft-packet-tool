package McForgeMods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Cette classe incarne une version particulière d'un mod.
 * Elle rassemble toutes les informations relatives à une version, n'apparaissant pas dans {@link Mod}.
 * L'association à un fichier réel {@link #urls} est optionnelle.
 *
 * @see Mod
 */
public class ModVersion {
	public final Mod     mod;
	public final Version version;
	public final Version mcversion;
	
	/**
	 * Liste des liens menant au fichier (localement ou un url)
	 */
	public final List<URL>                      urls         = new ArrayList<>(1);
	/**
	 * Mods obligatoires pour le bon fonctionnement de celui-ci.
	 * Une intervalle de version doit être spécifiée.
	 */
	public final Map<String, VersionIntervalle> requiredMods = new HashMap<>();
	/**
	 * Mods, si présents, à charger avant celui-ci.
	 * Aucune intervalle de version n'est nécessaire.
	 */
	public final List<String>                   dependants   = new ArrayList<>(0);
	public final List<String>                   alias        = new ArrayList<>(0);

    /*
    List<String> authorList;
    String credits;
    String logoFile;
    List<String> screenshots;
     */
	
	public ModVersion(Mod mod, Version version, Version mcversion) {
		this.mod = mod;
		this.version = version;
		this.mcversion = mcversion;
	}
	
	/**
	 * Constructeur utilisé pour initialisé une version à partir des informations d'un dépot.
	 */
	public ModVersion(Mod mod, Version version, JSONObject json) {
		this.mod = mod;
		this.version = version;
		this.mcversion = Version.read(json.getString("mcversion"));
		
		if (json.has("urls")) {
			Object url = json.get("urls");
			if (url instanceof JSONArray) {
				for (int i = 0; i < ((JSONArray) url).length(); i++)
					try {
						this.ajoutURL(new URL(((JSONArray) url).getString(i)));
					} catch (MalformedURLException u) {
						u.printStackTrace();
					}
			} else {
				try {
					this.ajoutURL(new URL(json.getString("urls")));
				} catch (MalformedURLException u) {
					u.printStackTrace();
				}
			}
		}
		
		if (json.has("requiredMods")) {
			JSONArray liste = json.getJSONArray("requiredMods");
			for (int i = 0; i < liste.length(); i++) {
				String required = liste.getString(i);
				int at = required.indexOf('@');
				if (at != -1) {
					this.ajoutModRequis(required.substring(0, at), VersionIntervalle.read(required.substring(at + 1)));
				} else {
					this.ajoutModRequis(required, new VersionIntervalle());
				}
			}
		}
		
		if (json.has("dependants")) {
			JSONArray liste = json.getJSONArray("dependants");
			for (int i = 0; i < liste.length(); i++) {
				this.ajoutDependant(liste.getString(i));
			}
		}
		
		if (json.has("alias")) {
			JSONArray liste = json.getJSONArray("alias");
			for (int i = 0; i < liste.length(); i++)
				this.ajoutAlias(liste.getString(i));
		}
	}
	
	/**
	 * Ajoute un url sans doublons.
	 */
	public void ajoutURL(URL url) {
		if (!this.urls.contains(url)) this.urls.add(url);
	}
	
	/**
	 * Ajoute une nouvelle dépendance.
	 * Si un mod identique est déjà enregistrée, enregistre l'intersection entre les deux intervalles de version.
	 *
	 * @param modid      requis
	 * @param intervalle pour laquelle le modid est requis.
	 */
	public void ajoutModRequis(String modid, VersionIntervalle intervalle) {
		modid = modid.toLowerCase().intern();
		if (this.requiredMods.containsKey(modid)) this.requiredMods.get(modid).fusion(intervalle);
		else this.requiredMods.put(modid, intervalle);
	}
	
	/**
	 * Ajoute un nouveau mod comme dépendant du mod présent.
	 * Ajout sans doublons.
	 */
	public void ajoutDependant(String modid) {
		modid = modid.toLowerCase().intern();
		if (!this.dependants.contains(modid)) this.dependants.add(modid);
	}
	
	public void ajoutAlias(String alias) {
		if (!this.alias.contains(alias)) this.alias.add(alias);
	}
	
	/**
	 * Écrit le contenu du json associé à cette version.
	 *
	 * @param json: zone d'écriture
	 */
	public void json(JSONObject json) {
		this.urls.sort(Comparator.comparing(URL::toString));
		this.dependants.sort(String::compareTo);
		this.alias.sort(String::compareTo);
		
		json.put("mcversion", this.mcversion);
		json.put("urls", new JSONArray(this.urls));
		
		JSONArray liste = new JSONArray();
		this.requiredMods.keySet().stream().sorted().forEach(id -> liste.put(id + "@" + this.requiredMods.get(id)));
		json.put("requiredMods", liste);
		
		json.put("dependants", new JSONArray(this.dependants));
		json.put("alias", new JSONArray(this.alias));
	}
	
	/**
	 * Importe les nouvelles données à partir d'une instance similaire.
	 */
	public void fusion(ModVersion autre) {
		autre.requiredMods.forEach(this::ajoutModRequis);
		for (URL url : autre.urls)
			this.ajoutURL(url);
		for (String dep : autre.dependants)
			this.ajoutDependant(dep);
		for (String alias : autre.alias)
			this.ajoutAlias(alias);
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ModVersion that = (ModVersion) o;
		return mod.equals(that.mod) && version.equals(that.version);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(mod, version);
	}
	
	@Override
	public String toString() {
		return "ModVersion{" + "mod=" + mod.name + ", version=" + version + ", mcversion=" + mcversion + '}';
	}
}
