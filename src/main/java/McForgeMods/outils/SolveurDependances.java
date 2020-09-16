package McForgeMods.outils;

import McForgeMods.PaquetMinecraft;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.Depot;

import java.util.*;
import java.util.stream.Collectors;

public class SolveurDependances {
	public final  List<PaquetMinecraft>              selection   = new LinkedList<>();
	private final Depot                              depot;
	private final HashMap<String, VersionIntervalle> contraintes = new HashMap<>();
	
	public SolveurDependances(Depot depot) {
		this.depot = depot;
	}
	
	public SolveurDependances(SolveurDependances parent) {
		this(parent.depot);
		parent.contraintes.forEach((id, intervalle) -> contraintes.put(id, new VersionIntervalle(intervalle)));
		this.selection.addAll(parent.selection);
	}
	
	public Set<String> listeContraintes() {
		return this.contraintes.keySet();
	}
	
	public void ajoutContrainte(String id, VersionIntervalle zone) {
		if (contraintes.containsKey(id)) contraintes.get(id).intersection(zone);
		else contraintes.put(id, zone);
	}
	
	public VersionIntervalle contrainte(String id) {
		return this.contraintes.containsKey(id) ? this.contraintes.get(id) : VersionIntervalle.ouvert();
	}
	
	/**
	 * Fixe une version.
	 * <p>
	 * La version est fixée pour un {@link PaquetMinecraft} et les contraintes de dépendances propagées.
	 */
	public boolean ajoutSelection(PaquetMinecraft p) {
		if (!selection.contains(p)) {
			selection.add(p);
			this.ajoutContrainte(p.modid, new VersionIntervalle(p.version));
			return this.propagation(p);
		}
		return true;
	}
	
	/**
	 * Propage les contraintes.
	 * <p>
	 * La sélection d'une version particulière réduit les versions disponibles pour ses dépendances. Si une dépendance
	 * ne possède pas de solution, alors les dépendances ne peuvent pas être satisfaites pour les versions déjà
	 * sélectionnées. Si une dépendance ne possède plus qu'une seule solution, celle-ci est sélectionnée et les
	 * contraintes propagées.
	 *
	 * @param p: fixation de la version
	 * @return {@code true} si toujours solvable
	 */
	private boolean propagation(PaquetMinecraft p) {
		this.ajoutContrainte("minecraft", p.mcversion);
		final VersionIntervalle intervalle_minecraft = contrainte("minecraft");
		if (intervalle_minecraft.minimum().compareTo(intervalle_minecraft.maximum()) > 0) return false;
		
		for (String id : p.requiredMods.keySet()) {
			this.ajoutContrainte(id, p.requiredMods.get(id));
			
			final VersionIntervalle intervalle = contrainte(id);
			Optional<PaquetMinecraft> selection = this.selection.stream().filter(s -> s.modid.equals(id)).findFirst();
			// paquet déjà sélectionné
			if (selection.isPresent()) {
				if (intervalle.correspond(selection.get().version)) continue;
				else return false;
			}
			
			if (depot.contains(id)) {
				List<PaquetMinecraft> candidats = depot.getModVersions(id).stream()
						.filter(mv -> intervalle.correspond(mv.version)).collect(Collectors.toList());
				if (candidats.size() == 0) return false;
				else if (candidats.size() == 1 && !this.ajoutSelection(candidats.get(0))) return false;
			}
		}
		return true;
	}
	
	/**
	 * Cherche la version maximale possible dans la zone de version pour cet identifiant.
	 *
	 * @param id: identifiant
	 * @return la nouvelle solution incluant la nouvelle contrainte
	 */
	public SolveurDependances resolution(String id) {
		if (depot.contains(id)) {
			List<PaquetMinecraft> candidats = depot.getModVersions(id).stream()
					.filter(mv -> this.contrainte(id).correspond(mv.version)).sorted(Comparator.reverseOrder())
					.collect(Collectors.toList());
			for (PaquetMinecraft c : candidats) {
				SolveurDependances solveur = new SolveurDependances(this);
				if (solveur.ajoutSelection(c)) return solveur;
			}
			return null;
		}
		return new SolveurDependances(this);
	}
	
	/**
	 * Tente de résoudre tous les identifiants en même temps.
	 * <p>
	 * Après avoir déclarer les contraintes sur les identifiants, il est possible de résoudre les versions pour une
	 * liste d'identifiant.
	 */
	public SolveurDependances resolutionTotale() {
		Optional<String> suivant = this.contraintes.keySet().stream().filter(depot::contains)
				.filter(c -> this.selection.stream().noneMatch(s -> s.modid.equals(c))).findAny();
		if (suivant.isEmpty()) return this;
		else {
			SolveurDependances solveur = this.resolution(suivant.get());
			return solveur == null ? null : solveur.resolutionTotale();
		}
	}
}
