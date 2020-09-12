package McForgeMods.depot;

import McForgeMods.ModVersion;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;

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
	protected final Map<String, Set<ModVersion>> mod_version = new HashMap<>();
	
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
	public Set<ModVersion> getModVersions(String modid) {
		return this.mod_version.get(modid);
	}
	
	/**
	 * Cherche une version particulière d'un mod. Pour vérifier qu'une version est disponible, utiliser {@link
	 * #contains(ModVersion)}.
	 *
	 * @return {@link Optional<ModVersion>} si la version est disponible ou non.
	 */
	public Optional<ModVersion> getModVersion(String mod, Version version) {
		return this.contains(mod) ? this.getModVersions(mod).stream().filter(mv -> mv.version.equals(version)).findAny()
				: Optional.empty();
	}
	
	public boolean contains(String modid) {
		return this.mod_version.containsKey(modid.toLowerCase());
	}
	
	/**
	 * @return {@code true} si le mod à la version demandée est connu de ce dépôt.
	 */
	public boolean contains(ModVersion modVersion) {
		return this.contains(modVersion.modid) && this.mod_version.get(modVersion.modid).contains(modVersion);
	}
	
	/**
	 * Enregistre une nouvelle version d'un mod dans le dépot.
	 */
	public ModVersion ajoutModVersion(final ModVersion modVersion) {
		if (!mod_version.containsKey(modVersion.modid)) mod_version.put(modVersion.modid, new HashSet<>());
		
		final Collection<ModVersion> liste = this.mod_version.get(modVersion.modid);
		Optional<ModVersion> present = liste.stream().filter(m -> m.version.equals(modVersion.version)).findFirst();
		if (present.isPresent()) {
			present.get().fusion(modVersion);
			return present.get();
		} else {
			liste.add(modVersion);
			return modVersion;
		}
	}
	
	/**
	 * Recherche parmi les informations de ce dépot, une version compatible avec cet alias. En général, l'alias est un
	 * nom de fichier. Si cet alias contient le symbole '-', alors l'hypothèse est que le nom est de la forme
	 * <i>modid-version</i> et la recherche à lien en priorité sur les versions correspondants au <i>modid</i>.
	 *
	 * @return la première version trouvée, {@link Optional#empty()} sinon.
	 */
	public Optional<ModVersion> rechercheAlias(String nom) {
		int i = nom.indexOf('-');
		if (i > 0) {
			String test = nom.substring(0, i).toLowerCase();
			if (this.contains(test)) {
				for (ModVersion version : this.getModVersions(test)) {
					if (version.alias.contains(nom)) return Optional.of(version);
				}
			}
		}
		
		for (String modid : this.getModids()) {
			for (ModVersion version : this.getModVersions(modid))
				if (version.alias.contains(nom)) return Optional.of(version);
		}
		return Optional.empty();
	}
	
	/**
	 * Fait la liste des versions absentes.
	 * <p>
	 * Parmis les dépendances fournies en entrée, cherche dans le dépot, si une version compatible existe. !!! Ne
	 * compare pas les versions minecraft, un intervalle ouverte sur la droite est une mauvaise idée.
	 *
	 * @return une map {modid -> version} des demandes qui n'ont pas trouvée de correspondance.
	 */
	public Map<String, VersionIntervalle> dependancesAbsentes(final Map<String, VersionIntervalle> demande) {
		final Map<String, VersionIntervalle> absents = new HashMap<>();
		for (Map.Entry<String, VersionIntervalle> dep : demande.entrySet()) {
			if (!this.contains(dep.getKey()) || this.getModVersions(dep.getKey()).stream().noneMatch(
					m -> dep.getValue().equals(VersionIntervalle.ouvert()) || dep.getValue().correspond(m.version)))
				absents.put(dep.getKey(), dep.getValue());
		}
		return absents;
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
