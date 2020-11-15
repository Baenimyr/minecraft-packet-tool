package McForgeMods.solveur;

import McForgeMods.Version;
import McForgeMods.VersionIntervalle;

import java.util.Collections;
import java.util.Objects;

/**
 * Contrainte de dépendance.
 * <p>
 * Une version est disponible, seulement si toutes ses dépendances sont satisfiables.
 */
public class ContrainteDependance extends Contrainte<String, Version> {
	final String modid, modid_dep;
	final Version           version;
	final VersionIntervalle version_dep;
	
	public ContrainteDependance(final String modid, final Version version, final String modid_dep,
			final VersionIntervalle version_dep) {
		super(Collections.singleton(modid_dep));
		this.modid = modid;
		this.version = version;
		this.modid_dep = modid_dep;
		this.version_dep = version_dep;
	}
	
	public void reductionArc(final Solveur<String, Version> solveur) {
		if (!solveur.domaineVariable(modid).contains(version)) return;
		
		final Domaine<Version> domaine_dep = solveur.domaineVariable(modid_dep);
		if (domaine_dep.stream().filter(Objects::nonNull).noneMatch(version_dep::correspond) && solveur
				.domaineVariable(modid).remove(version)) {
			solveur.marquerVariable(modid);
		}
	}
	
	@Override
	public String toString() {
		return String.format("Contrainte {%s==%s => %s@%s}", modid, version, modid_dep, version_dep);
	}
}