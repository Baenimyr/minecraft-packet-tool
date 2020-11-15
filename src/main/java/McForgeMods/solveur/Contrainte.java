package McForgeMods.solveur;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Une contrainte implique la désactivation de certaines valeurs d'une variable lorsque la condition n'est plus vraie.
 * <p>
 * Une contrainte doit préciser toutes les variables surveillées. Si une de ces variables est modifiée, la contrainte
 * devra être recalculée.
 *
 * @param <K>: type des variables
 * @param <D>: type des valeurs associées aux variables
 */
public abstract class Contrainte<K, D> {
	public final ArrayList<K> variables;
	
	/** Crée une contrainte et indique toutes les variables surveillées. */
	public Contrainte(Collection<K> variables) {
		this.variables = new ArrayList<>(variables);
	}
	
	/**
	 * Vérifie la condition et peut désactiver certaines valeurs d'autres variables. Toute autre variable modifiée doit
	 * être notifiée au solveur avec {@link Solveur#marquerVariable(Object)} pour propager à nouveau les contraintes.
	 */
	public abstract void reductionArc(Solveur<K, D> solveur);
}
