package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.PaquetMinecraft;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.DepotLocal;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(name = "show", resourceBundle = "mcforgemods/lang/Show")
public class CommandeShow implements Callable<Integer> {
	@CommandLine.Mixin
	ForgeMods.Help help;
	
	@CommandLine.Option(names = {"-r", "--repo"})
	public Path depot = null;
	
	@CommandLine.Parameters(arity = "1..n", descriptionKey = "recherche")
	ArrayList<String> recherche;
	
	@Override
	public Integer call() {
		final DepotLocal depotLocal = new DepotLocal(depot);
		try {
			depotLocal.importation();
		} catch (IOException e) {
			System.err.printf("[ERROR] Erreur de lecture du dépot: %s %s%n", e.getClass(), e.getMessage());
		}
		
		final List<PaquetMinecraft> versions = new ArrayList<>();
		
		final Map<String, VersionIntervalle> demandes;
		try {
			demandes = VersionIntervalle.lectureDependances(recherche);
		} catch (IllegalArgumentException iae) {
			System.err.println("[ERROR] " + iae.getMessage());
			return 1;
		}
		
		for (Map.Entry<String, VersionIntervalle> rech : demandes.entrySet()) {
			if (!depotLocal.contains(rech.getKey())) System.err.printf("Mod inconnu: '%s'%n", rech.getKey());
			else {
				List<PaquetMinecraft> modVersion = depotLocal.getModVersions(rech.getKey()).stream()
						.filter(v -> rech.getValue().contains(v.version)).collect(Collectors.toList());
				if (modVersion.size() > 0) versions.addAll(modVersion);
				else System.err.printf("Aucune version disponible pour '%s@%s'%n", rech.getKey(), rech.getValue());
			}
		}
		
		versions.sort(Comparator.reverseOrder());
		for (PaquetMinecraft version : versions) {
			System.out.printf("%s %s%n", version.nomCommun == null ? version.modid : "\"" + version.nomCommun + "\"",
					version.version);
			System.out.println("section: " + version.section.name());
			StringJoiner joiner = new StringJoiner(", ");
			version.requiredMods.entrySet().stream().sorted(Map.Entry.comparingByKey())
					.forEach(e -> joiner.add(e.getKey() + "@" + e.getValue()));
			System.out.println("dependencies: " + joiner.toString());
			if (version.description != null) System.out.println("description: " + version.description);
			System.out.println();
		}
		
		return 0;
	}
}
