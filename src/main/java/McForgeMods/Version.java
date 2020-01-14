package McForgeMods;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Permet de décomposer et comparer les versions entre-elles.
 * Une version valide est composés de chiffres séparés par des virgules.
 * Le format minimum est <i>major.minor</i>. Le format le plus large est <i>major.medium.minor.patch</i>.
 *
 * @see <a href="https://semver.org">Semantic Versioning</a>
 * @since 2020-01-10
 */
public class Version implements Comparable<Version> {
    private static final Pattern version = Pattern.compile("^(?<major>\\p{Digit}+)(\\.(?<medium>\\p{Digit}+)(\\.(?<minor>\\p{Digit}+)(\\.(?<patch>\\p{Digit}+))?)?)?");
    /**
     * major, medium, minor, patch
     */
    private final int[] versions = new int[4];

    public Version(int major, int medium, int minor, int patch) {
        this.versions[0] = major;
        this.versions[1] = medium;
        this.versions[2] = minor;
        this.versions[3] = patch;
    }

    public Version(int major, int medium, int minor) {
        this(major, medium, minor, 0);
    }

    public Version(Version v) {
        System.arraycopy(v.versions, 0, this.versions, 0, 4);
    }

    public int size() {
        return 4;
    }

    public int get(int index) {
        return this.versions[index];
    }

    /** À utiliser avec prudence */
    public void set(int index, int valeur) {
        this.versions[index] = valeur;
    }

    public static Version read(String version) throws IllegalArgumentException {
        final int[] versions = new int[]{0, 0, 0, 0};
        Matcher m = Version.version.matcher(version);
        if (m.find()) {
            versions[0] = Integer.parseInt(m.group("major"));

            String medium = m.group("medium");
            String minor = m.group("minor");
            String patch = m.group("patch");
            versions[1] = medium != null ? Integer.parseInt(medium) : 0;
            versions[2] = minor != null ? Integer.parseInt(minor) : 0;
            versions[3] = patch != null ? Integer.parseInt(patch) : 0;

            return new Version(versions[0], versions[1], versions[2], versions[3]);
        } else {
            throw new IllegalArgumentException(String.format("La version '%s' n'a pas de format reconnu !", version));
        }
    }

    @Override
    public int compareTo(Version version) {
        if (this.versions[0] == version.versions[0]) {
            if (this.versions[1] == version.versions[1]) {
                if (this.versions[2] == version.versions[2]) {
                    if (this.versions[3] == version.versions[3]) {
                        return 0;
                    } else return Integer.compare(this.versions[3], version.versions[3]);
                } else return Integer.compare(this.versions[2], version.versions[2]);
            } else return Integer.compare(this.versions[1], version.versions[1]);
        } else return Integer.compare(this.versions[0], version.versions[0]);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.versions[0] + "." + this.versions[1] + "." + this.versions[2]);
        if (this.versions[3] != 0) {
            sb.append('.');
            sb.append(this.versions[3]);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Arrays.equals(versions, ((Version) o).versions);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(versions);
    }
}
