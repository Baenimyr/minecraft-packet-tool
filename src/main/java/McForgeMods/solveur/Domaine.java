package McForgeMods.solveur;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Le domaine de valeurs disponibles pour une variable.
 * <p>
 * Les valeurs du domaine ne sont jamais perdus, {@link #push()} permet de sauvegarder l'état du domaine et {@link
 * #pop()} restaure le dernier état de l'historique.
 */
public class Domaine<D> implements Iterable<D> {
	private final LinkedList<D>  valeurs;
	private final Deque<Integer> limites = new LinkedList<>();
	private       int            limite;
	
	public Domaine(Collection<D> valeurs) {
		this.valeurs = new LinkedList<>(valeurs);
		this.limite = this.valeurs.size();
	}
	
	public int size() {
		return this.limite;
	}
	
	public D get(int i) {
		return this.valeurs.get(i);
	}
	
	public boolean contains(final D v) {
		for (int i = 0; i < this.size(); i++) {
			if (Objects.equals(this.get(i), v)) return true;
		}
		return false;
	}
	
	protected boolean remove(final int i) {
		if (0 <= i && i < this.limite) {
			this.valeurs.add(this.limite, this.get(i));
			this.valeurs.remove(i);
			this.limite--;
			return true;
		}
		return false;
	}
	
	/**
	 * Désactive une valeurs du domaine.
	 *
	 * @param v: valeur à désactiver
	 * @return {@code true} si la valeur existe et a été désactivée
	 */
	public boolean remove(final D v) {
		int i = this.valeurs.indexOf(v);
		return this.remove(i);
	}
	
	public boolean removeIf(Predicate<D> predicate) {
		boolean modifie = false;
		for (int i = this.size() - 1; i >= 0; i--)
			if (predicate.test(this.get(i))) {
				modifie |= this.remove(this.get(i));
			}
		return modifie;
	}
	
	/**
	 * Désactive toutes les valeurs exceptée une.
	 *
	 * @return {@code true} si cette valeur existe et qu'elle n'a pas déjà été désactivée.
	 */
	public boolean reduction(final D v) {
		int i = this.valeurs.indexOf(v);
		if (i == 0 && this.limite == 1) return false;
		else if (i == 0 && this.limite > 1) {
			this.limite = 1;
			return true;
		} else if (1 <= i && i < this.limite) {
			Collections.swap(this.valeurs, 0, i);
			this.limite = 1;
			return true;
		} else if (this.limite > 0) {
			this.limite = 0;
			return true;
		}
		return false;
	}
	
	public void push() {
		this.limites.add(this.limite);
	}
	
	public void pop() {
		if (this.limites.size() > 0) this.limite = this.limites.removeLast();
		else this.limite = this.valeurs.size();
	}
	
	@Override
	public Iterator<D> iterator() {
		return this.valeurs.subList(0, this.limite).iterator();
	}
	
	public Stream<D> stream() {
		return this.valeurs.subList(0, this.limite).stream();
	}
	
	@Override
	public String toString() {
		StringJoiner join = new StringJoiner(", ");
		for (int i = 0; i < this.size(); i++)
			join.add(this.get(i).toString());
		return "Domaine{" + join.toString() + "}";
	}
}
