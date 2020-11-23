package McForgeMods.commandes;

import McForgeMods.depot.DepotInstallation;
import org.apache.commons.vfs2.FileSystemException;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "remove", resourceBundle = "mcforgemods/lang/Remove")
public class CommandeRemove implements Callable<Integer> {
	@CommandLine.Option(names = {"-m", "--minecraft"})
	Path minecraft;
	
	@CommandLine.Parameters(arity = "1..n", descriptionKey = "mods")
	List<String> mods;
	
	@CommandLine.Option(names = {"-f", "--force"})
	boolean force;
	
	@Override
	public Integer call() {
		final DepotInstallation depotInstallation;
		try {
			depotInstallation = DepotInstallation.depot(minecraft);
			depotInstallation.statusImportation();
		} catch (FileSystemException e) {
			System.err.println("Erreur de lecture du dépôt.");
			return 1;
		}
		
		
		boolean erreur = false;
		for (String id : mods) {
			if (!depotInstallation.contains(id)) {
				System.err.printf("%s n'est pas installé !%n", id);
				erreur = true;
			}
			if (!force && depotInstallation.getModids().stream().filter(i -> !mods.contains(i))
					.map(depotInstallation::informations).map(i -> i.paquet)
					.anyMatch(mv -> mv.requiredMods.containsKey(id))) {
				System.err.printf("Impossible de supprimer '%s' car il est une dépendance.%n", id);
				erreur = true;
			}
		}
		if (erreur) return 1;
		
		for (String id : mods) {
			try {
				System.out.println("Suppression de " + id);
				if (!depotInstallation.desinstallation(id)) {
					System.err.printf("Impossible de supprimer %s: aucune données%n", id);
				}
			} catch (FileSystemException e) {
				e.printStackTrace();
				return 2;
			}
		}
		
		try {
			depotInstallation.close();
		} catch (IOException e) {
			System.err.println("Impossible de sauvegarder la configuration de l'installation.");
			return 2;
		}
		return 0;
	}
}
