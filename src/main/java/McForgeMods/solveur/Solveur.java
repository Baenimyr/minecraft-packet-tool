package McForgeMods.solveur;

import java.util.*;

public class Solveur<K, D> {
	protected final Map<K, Domaine<D>>             domaines    = new HashMap<>();
	protected final Map<K, List<Contrainte<K, D>>> contraintes = new HashMap<>();
	private final   LinkedList<K>                  modifies    = new LinkedList<>();
	
	public Set<K> variables() {
		return this.domaines.keySet();
	}
	
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
	 * @param id identifiant de la variable.
	 */
	public void marquerVariable(final K id) {
		assert this.domaines.containsKey(id);
		if (!this.modifies.contains(id)) {
			this.modifies.add(id);
		}
	}
	
	/**
	 * Assure la cohérence des dépendances.
	 *
	 * @return {@code true} si le solveur est dans un état cohérent.
	 */
	public boolean coherence() {
		boolean coherent = true;
		while (!modifies.isEmpty()) {
			final K modid = modifies.removeFirst();
			if (this.domaineVariable(modid).size() == 0) {
				coherent = false;
				continue;
			}
			
			for (final Contrainte<K, D> contrainte : this.contraintes.get(modid)) {
				contrainte.reductionArc(this);
			}
		}
		return coherent;
	}
	
	/**
	 * Sélectionne une variable qui possède encore des libertés.
	 */
	private Optional<K> cleLibre() {
		for (final Map.Entry<K, Domaine<D>> variable : this.domaines.entrySet()) {
			if (variable.getValue().size() > 1) return Optional.of(variable.getKey());
		}
		return Optional.empty();
	}
	
	/**
	 * Tente de résoudre toutes les contraintes et sélectionne une valeurs par variable déclarée.
	 *
	 * @return {@code true} si la résolution est possible
	 */
	public boolean resolution() {
		if (!this.coherence()) return false;
		final LinkedList<K> historique = new LinkedList<>();
		
		Optional<K> variable = this.cleLibre();
		while (variable.isPresent()) {
			this.domaines.values().forEach(Domaine::push);
			final K var = variable.get();
			final Domaine<D> domaine = this.domaineVariable(var);
			
			if (domaine.size() > 0) {
				final D valeur = domaine.get(0);
				domaine.reduction(valeur);
				
				this.marquerVariable(var);
				if (this.coherence()) {
					// enregistre l'historique
					historique.push(var);
					variable = this.cleLibre();
				} else {
					this.domaines.values().forEach(Domaine::pop);
					// désactive la valeur problématique
					domaine.remove(valeur);
					if (domaine.size() != 0) this.domaines.values().forEach(Domaine::push);
				}
			} else {
				if (historique.size() == 0) return false;
				else {
					// rétablissement de l'historique au dernier état valide et désactivation de la dernière valeur
					// choisie.
					final K h = historique.removeLast();
					final D erreur = this.domaineVariable(h).get(0);
					this.domaines.values().forEach(Domaine::pop);
					this.domaineVariable(h).remove(erreur);
					this.marquerVariable(h);
					variable = Optional.of(h);
				}
			}
		}
		return true;
	}
}
