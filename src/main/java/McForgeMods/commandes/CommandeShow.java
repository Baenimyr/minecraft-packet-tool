package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.Mod;
import McForgeMods.ModVersion;
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
			System.err
					.println(String.format("[ERROR] Erreur de lecture du dépot: %s %s", e.getClass(), e.getMessage()));
		}
		final List<Mod> mods = new ArrayList<>();
		final List<ModVersion> versions = new ArrayList<>();
		
		final Map<String, VersionIntervalle> demandes;
		try {
			demandes = VersionIntervalle.lectureDependances(recherche);
		} catch (IllegalArgumentException iae) {
			System.err.println("[ERROR] " + iae.getMessage());
			return 1;
		}
		
		for (Map.Entry<String, VersionIntervalle> rech : demandes.entrySet()) {
			Mod mod = depotLocal.getMod(rech.getKey());
			if (mod == null) System.err.println(String.format("Mod inconnu: '%s'", rech.getKey()));
			else if (rech.getValue().equals(VersionIntervalle.ouvert())) {
				mods.add(mod);
			} else {
				List<ModVersion> modVersion = depotLocal.getModVersions(mod).stream()
						.filter(v -> rech.getValue().correspond(v.version)).collect(Collectors.toList());
				if (modVersion.size() > 0) versions.addAll(modVersion);
				else System.err.println(
						String.format("Aucune version disponible pour '%s@%s'", rech.getKey(), rech.getValue()));
			}
		}
		
		for (Mod mod : mods) {
			System.out.println(String.format("%s (%s):%n'%s'%n{url='%s', updateJSON='%s'}", mod.name, mod.modid,
					mod.description != null ? mod.description : "", mod.url != null ? mod.url : "",
					mod.updateJSON != null ? mod.updateJSON : ""));
			StringJoiner joiner = new StringJoiner(" ");
			depotLocal.getModVersions(mod).stream().sorted(Comparator.comparing(mv -> mv.version))
					.forEach(mv -> joiner.add(mv.version.toString()));
			System.out.println("versions: " + joiner.toString());
			System.out.println();
		}
		
		for (ModVersion version : versions) {
			System.out.println(
					String.format("%s %s [%s]", version.modid, version.version, version.mcversion.toStringMinimal()));
			StringJoiner joiner = new StringJoiner(",");
			version.requiredMods.entrySet().stream().sorted(Map.Entry.comparingByKey())
					.forEach(e -> joiner.add(e.getKey() + "@" + e.getValue()));
			System.out.println("requiredMods: " + joiner.toString());
			System.out.println("urls:\t" + Arrays.toString(version.urls.toArray()));
			System.out.println("alias:\t" + Arrays.toString(version.alias.toArray()));
			System.out.println();
		}
		
		return 0;
	}
}
