package McForgeMods.depot;

import java.io.IOException;
import java.io.InputStream;

public interface DepotDistant {
	
	InputStream fichierIndexDepot() throws IOException;
	
	InputStream fichierModDepot(final String modid) throws IOException;
}
