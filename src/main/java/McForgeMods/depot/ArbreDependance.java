package McForgeMods.depot;

import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;

import java.util.*;

/**
 * L'arbre de dépendance montre comment les différents sont reliés entre eux. Chaque noeud représente un mod et une
 * intervalle de version valide, cependant un mod ne peut apparaitre qu'une seule fois dans l'arbre.
 */
public class ArbreDependance {
	final Map<String, VersionIntervalle> mods        = new HashMap<>();
	final Map<String, Set<String>>       parents     = new HashMap<>();
	final Map<String, Set<String>>       dependances = new HashMap<>();
	
	public ArbreDependance() {
	
	}
	
	public ArbreDependance(Collection<ModVersion> versions) {
		versions.forEach(this::ajoutMod);
	}
	
	/** Fixe un version pour le mod et recalcul les intervalles de dépendance. */
	public void ajoutMod(ModVersion modVersion) {
		if (this.ajoutModIntervalle(modVersion.mod.modid, new VersionIntervalle(modVersion.version)))
			for (String modid : modVersion.requiredMods.keySet()) {
				this.ajoutDependance(modVersion.mod.modid, modid);
				this.ajoutModIntervalle(modid, modVersion.requiredMods.get(modid));
			}
	}
	
	/**
	 * Réduit l'intervalle de version disponible pour un mod.
	 * <p>
	 * La nouvelle intervalle sera l'intersection entre l'intervalle actuelle et la nouvelle
	 */
	public boolean ajoutModIntervalle(String mod, VersionIntervalle versions) {
		if (!mods.containsKey(mod)) {
			mods.put(mod, versions);
			parents.put(mod, new HashSet<>());
			dependances.put(mod, new HashSet<>());
			return true;
		} else if (mods.get(mod) == VersionIntervalle.ouvert) {
			mods.put(mod, versions);
			return versions != VersionIntervalle.ouvert;
		} else {
			final VersionIntervalle avant = new VersionIntervalle(mods.get(mod));
			mods.get(mod).intersection(versions);
			return !mods.get(mod).equals(avant);
		}
	}
	
	/**
	 * Enregistre une nouvelle dépendance.
	 * <p>
	 * Le mod requis est marqué comme nécessaire pour le mod parent.
	 *
	 * @param parent: mod à l'origine de la demande
	 * @param requis: mod requis par le mod parent
	 */
	public void ajoutDependance(String parent, String requis) {
		if (parents.containsKey(requis)) parents.get(requis).add(parent);
		else {
			parents.put(requis, new HashSet<>());
			parents.get(requis).add(parent);
		}
		
		if (dependances.containsKey(parent)) dependances.get(parent).add(requis);
		else {
			Set<String> dep = new HashSet<>();
			dep.add(requis);
			dependances.put(parent, dep);
		}
		this.ajoutModIntervalle(parent, VersionIntervalle.ouvert);
	}
	
	/**
	 * Etend le calcul des dépendances aux dépendances de dépendances.
	 * <p>
	 * La version la plus récente compatible du dépôt est utilisée pour déterminer les nouvelles dépendances.
	 */
	public void extension(Depot depot) {
		final LinkedList<String> temp = new LinkedList<>(this.mods.keySet());
		
		while (!temp.isEmpty()) {
			final String modid = temp.removeFirst();
			final VersionIntervalle vintervalle = this.mods.get(modid);
			
			if (depot.contains(modid)) {
				Optional<ModVersion> candidat = depot.getModVersions(modid).stream()
						.filter(mv -> vintervalle.correspond(mv.version)).max(Comparator.comparing(mv -> mv.version));
				
				if (candidat.isPresent()) {
					this.ajoutMod(candidat.get());
					final ModVersion mversion = candidat.get();
					for (String d_modid : mversion.requiredMods.keySet()) {
						if (!temp.contains(d_modid)) temp.addLast(d_modid);
					}
				}
			}
		}
	}
	
	public Set<String> listeModids() {
		return this.mods.keySet();
	}
	
	public VersionIntervalle intervalle(String modid) {
		return this.mods.getOrDefault(modid, VersionIntervalle.ouvert);
	}
	
	public Map<String, VersionIntervalle> requis() {
		return this.mods;
	}
	
	public boolean contains(ModVersion mversion) {
		return this.mods.containsKey(mversion.mod.modid) && this.mods.get(mversion.mod.modid).correspond(mversion.version);
	}
	
	public void clear() {
		this.mods.clear();
		this.parents.clear();
		this.dependances.clear();
	}
}
