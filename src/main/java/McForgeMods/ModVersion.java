package McForgeMods;

import java.net.URL;
import java.nio.file.Path;
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
	public final String            modid;
	public final Version           version;
	public final VersionIntervalle mcversion;
	
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
	public final List<String>                   alias        = new ArrayList<>(0);

    /*
    List<String> authorList;
    String credits;
    String logoFile;
    List<String> screenshots;
     */
	
	public ModVersion(String modid, Version version, VersionIntervalle mcversion) {
		this.modid = modid.intern();
		this.version = version;
		this.mcversion = mcversion;
	}
	
	/**
	 * Ajoute un url sans doublons.
	 */
	public void ajoutURL(URL url) {
		for (URL u : this.urls)
			if (u.toString().equals(url.toString()))
				return;
		this.urls.add(url);
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
		for (String alias : autre.alias)
			this.ajoutAlias(alias);
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ModVersion that = (ModVersion) o;
		return modid.equals(that.modid) && version.equals(that.version);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(modid, version);
	}
	
	@Override
	public String toString() {
		return String.format("'%s' %s %s", modid, version, mcversion);
	}
	
	public String toStringStandard() {
		return String.format("%s-%s-%s", modid, mcversion.toStringMinimal(), version);
	}
	
	/** Dossier dans lequel placer les fichiers lorsque le mod est installé.
	 * Ne donne pas le nom du fichier parce que celui-ci n'est pas standardisé.
	 *
	 * @param minecraft: dossier minecraft racine
	 * @return le dossier d'installation
	 */
	public Path dossierInstallation(Path minecraft) {
		return minecraft.resolve("mods");
	}
}
