package McForgeMods.depot;

import McForgeMods.PaquetMinecraft;
import McForgeMods.Version;

import java.util.*;

/**
 * Un dépôt est un ensemble de mod et leur versions. Il maintient une liste de mods connus et une liste de versions pour
 * ces mods. Un mod peut ne pas disposer de versions connues, cependant une version doit toujours disposer des
 * informations sur le mod.
 * <p>
 * Un exemple de dépot est le {@link DepotInstallation} qui représente les mods trouvés dans une installations
 * minecraft.
 */
public class Depot {
	protected final Map<String, Set<PaquetMinecraft>> mod_version = new HashMap<>();
	
	/**
	 * Renvoit la liste complète, sans doublons, des mods présents dans le dépôt.
	 */
	public Collection<String> getModids() {
		return this.mod_version.keySet();
	}
	
	/**
	 * Fournit la liste complète des version connues du mod. Si le mod n'est pas connu, renvoit {@code null}.
	 *
	 * @return un ensemble, ou {@code null}
	 */
	public Set<PaquetMinecraft> getModVersions(String modid) {
		return this.mod_version.get(modid);
	}
	
	/**
	 * Cherche une version particulière d'un mod. Pour vérifier qu'une version est disponible, utiliser {@link
	 * #contains(PaquetMinecraft)}.
	 *
	 * @return {@link Optional<PaquetMinecraft>} si la version est disponible ou non.
	 */
	public Optional<PaquetMinecraft> getModVersion(String mod, Version version) {
		return this.contains(mod) ? this.getModVersions(mod).stream().filter(mv -> mv.version.equals(version)).findAny()
				: Optional.empty();
	}
	
	public boolean contains(String modid) {
		return this.mod_version.containsKey(modid.toLowerCase());
	}
	
	/**
	 * @return {@code true} si le mod à la version demandée est connu de ce dépôt.
	 */
	public boolean contains(PaquetMinecraft modVersion) {
		return this.contains(modVersion.modid) && this.mod_version.get(modVersion.modid).contains(modVersion);
	}
	
	/**
	 * Enregistre une nouvelle version d'un mod dans le dépot.
	 * <p>
	 * Le dépôt ne peut contenir qu'un seul paquet par couple de modid-version.
	 *
	 * @return {@code false} si une version équivalente est déjà présente.
	 */
	public boolean ajoutModVersion(final PaquetMinecraft modVersion) {
		if (!mod_version.containsKey(modVersion.modid)) mod_version.put(modVersion.modid, new HashSet<>());
		return mod_version.get(modVersion.modid).add(modVersion);
	}
	
	/**
	 * @return le nombre de versions connues par ce dépot.
	 */
	public int sizeModVersion() {
		return this.mod_version.values().stream().mapToInt(Set::size).sum();
	}
	
	public void clear() {
		this.mod_version.clear();
	}
}
