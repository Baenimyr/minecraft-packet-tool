package McForgeMods;

import java.net.URL;
import java.util.*;

/**
 * Cette classe incarne une version particulière d'un mod.
 * <p>
 * Elle rassemble toutes les informations relatives à une version, n'apparaissant pas dans {@link Mod}. L'association à
 * un fichier réel {@link #urls} est optionnelle.
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
	 * Mods obligatoires pour le bon fonctionnement de celui-ci. Une intervalle de version doit être spécifiée ou nulle
	 * pour n'importe quelle version.
	 */
	public final Map<String, VersionIntervalle> requiredMods = new HashMap<>();
	/**
	 * Mods, si présents, à charger avant celui-ci. Aucune intervalle de version n'est nécessaire.
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
	 * Ajoute un url sans doublons.
	 */
	public void ajoutURL(URL url) {
		if (!this.urls.contains(url)) this.urls.add(url);
	}
	
	/**
	 * Ajoute une nouvelle dépendance.
	 * <p>
	 * Si un mod identique est déjà enregistrée, enregistre l'intersection entre les deux intervalles de version.
	 *
	 * @param modid      requis
	 * @param intervalle pour laquelle le modid est requis.
	 */
	public void ajoutModRequis(String modid, VersionIntervalle intervalle) {
		modid = modid.toLowerCase().intern();
		if (this.requiredMods.containsKey(modid) && this.requiredMods.get(modid) != null)
			this.requiredMods.get(modid).intersection(intervalle);
		else this.requiredMods.put(modid, intervalle);
	}
	
	/**
	 * Ajoute un nouveau mod comme dépendant du mod actuel. Ajout sans doublons.
	 */
	public void ajoutDependant(String modid) {
		modid = modid.toLowerCase().intern();
		if (!this.dependants.contains(modid)) this.dependants.add(modid);
	}
	
	public void ajoutAlias(String alias) {
		if (!this.alias.contains(alias)) this.alias.add(alias);
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
