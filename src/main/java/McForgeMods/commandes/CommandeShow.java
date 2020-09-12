package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.DepotLocal;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(name = "show", resourceBundle = "mcforgemods/lang/Show")
public class CommandeShow implements Callable<Integer> {
	@CommandLine.Mixin
	ForgeMods.Help help;
	
	@CommandLine.Option(names = {"-d", "--depot"})
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
		
		final List<ModVersion> versions = new ArrayList<>();
		
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
				List<ModVersion> modVersion = depotLocal.getModVersions(rech.getKey()).stream()
						.filter(v -> rech.getValue().correspond(v.version)).collect(Collectors.toList());
				if (modVersion.size() > 0) versions.addAll(modVersion);
				else System.err.println(
						String.format("Aucune version disponible pour '%s@%s'", rech.getKey(), rech.getValue()));
			}
		}
		
		for (ModVersion version : versions) {
			System.out.printf("%s %s [%s]: %s%n", version.modid, version.version, version.mcversion.toStringMinimal(),
					version.description);
			StringJoiner joiner = new StringJoiner(",");
			version.requiredMods.entrySet().stream().sorted(Map.Entry.comparingByKey())
					.forEach(e -> joiner.add(e.getKey() + "@" + e.getValue()));
			System.out.println("dependencies: " + joiner.toString());
			System.out.println();
		}
		
		return 0;
	}
}
