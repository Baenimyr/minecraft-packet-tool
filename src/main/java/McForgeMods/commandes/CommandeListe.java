package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.ModVersion;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import picocli.CommandLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandLine.Command(name = "list", resourceBundle = "mcforgemods/lang/List")
public class CommandeListe implements Callable<Integer> {
	
	@CommandLine.Mixin
	ForgeMods.DossiersOptions dossiers;
	@CommandLine.Mixin
	ForgeMods.Help            help;
	@CommandLine.Option(names = {"-i", "--no-installed"}, negatable = true, defaultValue = "true")
	private boolean installes;
	@CommandLine.ArgGroup(headingKey = "mode")
	private Mode    mode;
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
				List<ModVersion> versions = new ArrayList<>(depotLocal.getModVersions(modid));
				versions.sort(Comparator.comparing(mv -> mv.version));
				
				if (all || versions.size() > 0) {
					System.out.print(modid + ":");
					for (ModVersion mv : versions) System.out.print(" " + mv.version);
					System.out.println();
				}
			}
			return 0;
		} else {
			final DepotInstallation depotInstallation = new DepotInstallation(dossiers.minecraft);
			depotInstallation.analyseDossier(depotLocal);
			
			List<String> modids = new ArrayList<>(depotInstallation.getModids());
			modids.sort(String::compareTo);
			for (String modid : modids) {
				Stream<ModVersion> versions = depotInstallation.getModVersions(modid).stream();
				
				if (mode != null) {
					if (mode.manuels) versions = versions.filter(depotInstallation::estManuel);
					else if (mode.auto) versions = versions.filter(mv -> !depotInstallation.estManuel(mv));
					else if (mode.verrouille) versions = versions.filter(depotInstallation::estVerrouille);
				}
				
				List<ModVersion> liste = versions.sorted(Comparator.comparing(mv -> mv.version))
						.collect(Collectors.toList());
				
				if (all || liste.size() > 0) {
					System.out.print(modid + ":");
					for (ModVersion mv : liste) System.out.print(" " + mv.version);
					System.out.println();
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
