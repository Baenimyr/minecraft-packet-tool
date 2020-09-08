package McForgeMods.depot;

import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;

import java.util.*;

/**
 * L'arbre de dépendance montre comment les différents sont reliés entre eux. Chaque noeud représente un mod et une
 * intervalle de version valide, cependant un mod ne peut apparaitre qu'une seule fois dans l'arbre.
 */
public class ArbreDependance {
	/** Source d'informations. */
	private final Depot                          depot;
	/** Versions sélectionnées */
	private final Map<String, VersionIntervalle> contraintes = new HashMap<>();
	public final  VersionIntervalle              mcversion   = VersionIntervalle.ouvert();
	
	public ArbreDependance(Depot depot) {
		this.depot = depot;
	}
	
	public ArbreDependance(Depot depot, Collection<ModVersion> versions) {
		this(depot);
		versions.forEach(this::ajoutContrainte);
	}
	
	/** Fixe un version pour le mod et recalcul les intervalles de dépendance. */
	public void ajoutContrainte(ModVersion modVersion) {
		this.mcversion.intersection(modVersion.mcversion);
		this.ajoutContrainte(modVersion.mod.modid, new VersionIntervalle(modVersion.version));
	}
	
	/**
	 * Réduit l'intervalle de version disponible pour un mod.
	 * <p>
	 * La nouvelle intervalle sera l'intersection entre l'intervalle actuelle et la nouvelle
	 */
	public void ajoutContrainte(String modid, VersionIntervalle versions) {
		assert Objects.nonNull(versions);
		if (!contraintes.containsKey(modid)) {
			contraintes.put(modid, versions);
		} else {
			contraintes.get(modid).intersection(versions);
		}
	}
	
	/**
	 * Etend le calcul des dépendances aux dépendances de dépendances.
	 * <p>
	 * La version la plus récente compatible du dépôt est utilisée pour déterminer les nouvelles dépendances.
	 */
	public void resolution() throws IllegalArgumentException {
		final LinkedList<String> temp = new LinkedList<>(this.contraintes.keySet());
		
		while (!temp.isEmpty()) {
			final String modid = temp.removeFirst();
			final VersionIntervalle vintervalle = this.contraintes.getOrDefault(modid, VersionIntervalle.ouvert());
			
			if (depot.contains(modid)) {
				Optional<ModVersion> candidat = depot.getModVersions(modid).stream()
						.filter(mv -> mv.mcversion.englobe(this.mcversion))
						.filter(mv -> vintervalle.correspond(mv.version)).max(Comparator.comparing(mv -> mv.version));
				
				if (candidat.isPresent()) {
					final ModVersion mversion = candidat.get();
					this.ajoutContrainte(mversion);
					for (String d_modid : mversion.requiredMods.keySet()) {
						if (!temp.contains(d_modid)) temp.addLast(d_modid);
						this.ajoutContrainte(d_modid, mversion.requiredMods.get(d_modid));
					}
				}
			}
		}
	}
	
	public Set<String> listeModids() {
		return this.contraintes.keySet();
	}
	
	public VersionIntervalle intervalle(String modid) {
		return this.contraintes.getOrDefault(modid, VersionIntervalle.ouvert());
	}
	
	public Map<String, VersionIntervalle> requis() {
		return this.contraintes;
	}
	
	public boolean contains(ModVersion mversion) {
		return this.contraintes.containsKey(mversion.mod.modid) && this.contraintes.get(mversion.mod.modid).correspond(mversion.version);
	}
	
	public void clear() {
		this.contraintes.clear();
	}
}
