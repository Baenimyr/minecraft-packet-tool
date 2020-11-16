package McForgeMods.solveur;

import McForgeMods.Version;
import McForgeMods.VersionIntervalle;

import java.util.Arrays;
import java.util.Objects;

/**
 * Un paquet peut déclarer être en conflit avec un ensemble de version d'un autre paquet.
 * <p>
 * Si le paquet est choisi, alors toutes les versions en conflits doivent être désactivées. Si le paquet opposé ne
 * dispose plus que des versions conflictuelles, alors la version du paquet actuel doit être désactivée.
 */
public class ContrainteConflit<K> extends Contrainte<K, Version> {
	private final K                 paquet_id;
	private final Version           paquet_version;
	private final K                 conflit_id;
	private final VersionIntervalle conflit_versions;
	
	public ContrainteConflit(final K id, final Version version, final K conflit,
			final VersionIntervalle versions_conflit) {
		super(Arrays.asList(id, conflit));
		this.paquet_id = id;
		this.paquet_version = version;
		this.conflit_id = conflit;
		this.conflit_versions = versions_conflit;
	}
	
	@Override
	public void reductionArc(Solveur<K, Version> solveur) {
		final Domaine<Version> domaine = solveur.domaineVariable(this.paquet_id);
		final Domaine<Version> domaine_conflit = solveur.domaineVariable(this.conflit_id);
		if (domaine.size() == 0 || domaine_conflit.size() == 0) return;
		
		// la version a été choisie, conflit avec l'autre paquet
		if (domaine.size() == 1 && Objects.equals(domaine.get(0), this.paquet_version) && domaine_conflit
				.removeIf(this.conflit_versions::contains)) solveur.marquerVariable(this.conflit_id);
		// l'autre paquet est forcément en conflit
		if (domaine_conflit.stream().allMatch(conflit_versions::contains) && domaine.remove(this.paquet_version))
			solveur.marquerVariable(this.paquet_id);
	}
}
