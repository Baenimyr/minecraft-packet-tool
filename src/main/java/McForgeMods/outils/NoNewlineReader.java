package McForgeMods.outils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Supprime les sauts de ligne dans le flux, pour éviter les erreurs de lecture des fichiers JSON mal formatés.
 */
public class NoNewlineReader extends Reader {
    final Reader reader;

    public NoNewlineReader(Reader reader) {
        this.reader = reader;
    }

    public NoNewlineReader(InputStream input) {
        this.reader = new InputStreamReader(input);
    }

    @Override
    public int read(char[] chars, int offset, int len) throws IOException {
        int verifie = 0, end_buff = 0;
        while (verifie < len) {
            if (verifie == end_buff) {
                int lu = this.reader.read(chars, verifie, len - verifie);
                if (lu == -1)
                    return end_buff;
                end_buff += lu;
            }
            if (chars[verifie] == '\r' || chars[verifie] == '\n') {
                if (end_buff - verifie - 1 > 0)
                    System.arraycopy(chars, verifie + 1, chars, verifie, end_buff - verifie - 1);
                end_buff--;
            } else {
                verifie++;
            }
        }
        return 0;
    }

    @Override
    public void close() throws IOException {
        this.reader.close();
    }
}
