package McForgeMods.solveur;

import McForgeMods.PaquetMinecraft;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.Depot;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;

/**
 * Solveur de dépendance pour les paquets minecraft.
 * <p>
 * Les mods doivent être initialisés par {@link #initialisationMod(String)} qui permet d'enregistrer la variable et de
 * générer les contraintes de dépendances. Chaque version disponible dans le dépôt est ajoutée au domaine de la
 * variable, la valeur {@code null} correspond à un mod non installé. Tous mods utilisé pour la déclaration de
 * contrainte doit être chargé dans le solveur.
 */
public class SolveurPaquet extends Solveur<String, Version> {
	
	final Depot depot;
	
	public SolveurPaquet(Depot info, Version minecraft) {
		this.depot = info;
		this.ajoutVariable("minecraft", Collections.singleton(minecraft));
	}
	
	/**
	 * Initialise le domaine et les contraintes sortante d'un mod.
	 */
	public synchronized void initialisationMod(final String modid) {
		if (!domaines.containsKey(modid)) {
			LinkedList<Version> versions = new LinkedList<>();
			versions.add(null);
			if (depot.contains(modid)) {
				depot.getModVersions(modid).stream().map(p -> p.version).sorted(Comparator.reverseOrder())
						.forEach(versions::addLast);
			}
			this.ajoutVariable(modid, versions);
			this.marquerVariable(modid);
			
			for (final PaquetMinecraft paquet : depot.getModVersions(modid)) {
				for (Map.Entry<String, VersionIntervalle> dep : paquet.requiredMods.entrySet()) {
					final ContrainteDependance dependance = new ContrainteDependance(modid, paquet.version,
							dep.getKey(), dep.getValue());
					
					this.initialisationMod(dependance.modid_dep);
					this.ajoutContrainte(dependance);
					this.marquerVariable(dep.getKey());
				}
			}
		}
	}
	
	@Override
	public Domaine<Version> domaineVariable(final String id) {
		if (!domaines.containsKey(id)) this.initialisationMod(id);
		return super.domaineVariable(id);
	}
	
	// -+-+-+-+-+-+-+-+-+-+
	
	public void init(Map<String, VersionIntervalle> demandes) {
		for (final String modid : demandes.keySet()) {
			this.initialisationMod(modid);
			final VersionIntervalle intervalle = demandes.get(modid);
			this.domaineVariable(modid).removeIf(version -> !intervalle.contains(version));
			this.domaineVariable(modid).push();
		}
	}
}
