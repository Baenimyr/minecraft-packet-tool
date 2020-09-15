package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import org.apache.commons.vfs2.FileSystemException;
import picocli.CommandLine;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "remove")
public class CommandeRemove implements Callable<Integer> {
	@CommandLine.Mixin
	ForgeMods.DossiersOptions dossiers;
	
	@CommandLine.Parameters(arity = "1..n", descriptionKey = "mods")
	List<String> mods;
	
	@Override
	public Integer call() {
		DepotLocal depotLocal = new DepotLocal(dossiers.depot);
		DepotInstallation depotInstallation = new DepotInstallation(depotLocal, dossiers.minecraft);
		
		depotInstallation.statusImportation();
		
		for (String id : mods) {
			if (!depotInstallation.contains(id)) {
				System.err.printf("%s n'est pas installé !", id);
				continue;
			}
			try {
				if (!depotInstallation.desinstallation(id)) {
					System.err.printf("Impossible de supprimer %s: aucune données", id);
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
			return 1;
		}
		return 0;
	}
}
