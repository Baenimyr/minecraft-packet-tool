package McForgeMods;

import java.util.Objects;

/**
 * Cette classe incarne un mod dans sa généralité. Elle contient toutes les informations indépendantes des versions
 * associées à un <i>modid</i>. Ces informations se retrouveront sauvegardées dans le fichier <i>Mods.xz</i> en tête de
 * dépôt.
 */
public class Mod implements Comparable<Mod> {
	
	
	public final String modid;
	public       String name        = null;
	public       String url         = null;
	public       String description = null;
	public       String updateJSON  = null;
	
	public Mod(String modid) {
		Objects.requireNonNull(modid);
		this.modid = modid.toLowerCase().intern();
	}
	
	public Mod(String modid, String name) {
		Objects.requireNonNull(modid);
		Objects.requireNonNull(name);
		this.modid = modid.toLowerCase().intern();
		this.name = name;
	}
	
	/**
	 * Récupère les informations utiles dans l'autre instance de mod.
	 */
	public void fusion(Mod mod) {
		if (!this.modid.equals(mod.modid)) return;
		if (this.name == null) this.name = mod.name;
		if (this.url == null) this.url = mod.url;
		if (this.description == null) this.description = mod.description;
		if (this.updateJSON == null) this.updateJSON = mod.updateJSON;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Mod mod = (Mod) o;
		return modid.equals(mod.modid);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(modid);
	}
	
	@Override
	public String toString() {
		return "Mod{" + "modid='" + modid + '\'' + ", name='" + name + '\'' + ", description='" + description + '\''
				+ '}';
	}
	
	@Override
	public int compareTo(Mod mod) {
		return this.modid.compareTo(mod.modid);
	}
}
