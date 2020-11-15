package McForgeMods.solveur;

import java.util.*;

public class Solveur<K, D> {
	protected final Map<K, Domaine<D>>             domaines    = new HashMap<>();
	protected final Map<K, List<Contrainte<K, D>>> contraintes = new HashMap<>();
	private final   LinkedList<K>                  modifies    = new LinkedList<>();
	
	/** Enregistre une nouvelle variable et initialise son domaine. */
	public void ajoutVariable(final K id, Collection<D> versions) {
		assert !domaines.containsKey(id);
		this.domaines.put(id, new Domaine<>(versions));
		this.contraintes.put(id, new ArrayList<>());
		this.marquerVariable(id);
	}
	
	/** Enregistre une nouvelle contrainte. Les variables utilisées doivent avoir été enregistrées. */
	public void ajoutContrainte(final Contrainte<K, D> dependance) {
		for (final K id : dependance.variables) {
			assert contraintes.containsKey(id);
			contraintes.get(id).add(dependance);
		}
	}
	
	/** Retourne le domaine de valeur associé à une variable. */
	public Domaine<D> domaineVariable(final K id) {
		assert this.domaines.containsKey(id);
		return this.domaines.get(id);
	}
	
	/**
	 * Marque la modification du domaine d'une variable. La contrainte doit ensuite être propagée en utilisant la
	 * fonction {@link #coherence()}.
	 *
	 * @param id: identifiant de la variable.
	 */
	public void marquerVariable(final K id) {
		assert this.domaines.containsKey(id);
		if (!this.modifies.contains(id)) {
			this.modifies.add(id);
		}
	}
	
	/**
	 * Assure la cohérence des dépendances.
	 */
	public void coherence() {
		while (!modifies.isEmpty()) {
			final K modid = modifies.removeFirst();
			for (final Contrainte<K, D> contrainte : this.contraintes.get(modid)) {
				contrainte.reductionArc(this);
			}
		}
	}
}
