package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.PaquetMinecraft;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import picocli.CommandLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "list", resourceBundle = "mcforgemods/lang/List")
public class CommandeListe implements Callable<Integer> {
	
	@CommandLine.Mixin
	ForgeMods.DossiersOptions dossiers;
	@CommandLine.Mixin
	ForgeMods.Help            help;
	@CommandLine.Option(names = {"-i", "--no-installed"}, negatable = true, defaultValue = "true")
	private boolean installes;
	@CommandLine.ArgGroup(headingKey = "mode")
	private Mode    mode = null;
	@CommandLine.Option(names = {"-a", "--all-versions"})
	private boolean all;
	
	@Override
	public Integer call() {
		final DepotLocal depotLocal = new DepotLocal(dossiers.depot);
		try {
			depotLocal.importation();
		} catch (IOException io) {
			System.err.println("Impossible de lire le dépôt local: " + io.getMessage());
			return -1;
		}
		
		if (all || !installes) {
			List<String> modids = new ArrayList<>(depotLocal.getModids());
			modids.sort(String::compareTo);
			for (String modid : modids) {
				List<PaquetMinecraft> versions = new ArrayList<>(depotLocal.getModVersions(modid));
				versions.sort(Comparator.comparing(mv -> mv.version));
				
				if (all || versions.size() > 0) {
					System.out.print(modid + ":");
					for (PaquetMinecraft mv : versions) System.out.print(" " + mv.version);
					System.out.println();
				}
			}
			return 0;
		} else {
			final DepotInstallation depotInstallation = new DepotInstallation(depotLocal, dossiers.minecraft);
			depotInstallation.analyseDossier();
			
			List<String> modids = new ArrayList<>(depotInstallation.getModids());
			modids.sort(String::compareTo);
			for (String modid : modids) {
				DepotInstallation.Installation ins = depotInstallation.informations(modid);
				
				if (mode == null || (mode.manuels && ins.manuel) || (mode.auto && !ins.manuel) || (mode.verrouille
						&& ins.verrou)) {
					System.out.println(modid + ":" + ins.version);
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
	
	static class Mode {
		@CommandLine.Option(names = {"--manual"})
		boolean manuels;
		
		@CommandLine.Option(names = "--auto")
		boolean auto;
		
		@CommandLine.Option(names = "--locked")
		boolean verrouille;
	}
}
