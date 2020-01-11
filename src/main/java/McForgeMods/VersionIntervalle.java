package McForgeMods;

import java.util.Objects;

public class VersionIntervalle {
    Version minimum, maximum;
    boolean inclut_min, inclut_max;

    public VersionIntervalle() {
        this(null, null);
        this.inclut_min = this.inclut_max = true;
    }

    public VersionIntervalle(Version minimum, Version maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /**
     * Crée une dépendance n'acceptant qu'une seule version.
     * Par exemple la présence d'un mod déjà installé, contraint la résolution de version à celle disponible.
     */
    public VersionIntervalle(Version version) {
        this.minimum = this.maximum = version;
        this.inclut_min = this.inclut_max = true;
    }

    public static VersionIntervalle read(String contraintes) throws VersionIntervalleFormatException {
        VersionIntervalle d = new VersionIntervalle();

        int pos = 0;
        if (!Character.isDigit(pos)) {
            d.inclut_min = contraintes.charAt(pos) == '[';
            pos++;
        }

        int virgule = pos;
        while (virgule < contraintes.length()
                && (contraintes.charAt(virgule) == '.' || Character.isDigit(contraintes.charAt(virgule))))
            virgule++;

        if (virgule > pos)
            d.minimum = Version.read(contraintes.substring(pos, virgule));
        pos = virgule;

        if (contraintes.charAt(pos) == ',') {
            virgule = ++pos;
            while (virgule < contraintes.length()
                    && (contraintes.charAt(virgule) == '.' || Character.isDigit(contraintes.charAt(virgule))))
                virgule++;

            if (virgule > pos) {
                d.maximum = Version.read(contraintes.substring(pos, virgule));
                pos = virgule;
            }

        } else if (d.minimum != null) {
            d.maximum = new Version(d.minimum);
        } else {
            throw new VersionIntervalleFormatException(contraintes);
        }

        if (contraintes.length() != pos + 1)
            throw new VersionIntervalleFormatException(contraintes);
        else
            d.inclut_max = contraintes.charAt(pos) == ']';

        return d;
    }

    public void fusion(VersionIntervalle d) {
        if (Objects.equals(minimum, d.minimum))
            inclut_min = !(!inclut_min || !d.inclut_min);
        else if (minimum == null)
            minimum = d.minimum;
        else if (d.minimum != null) {
            minimum = minimum.compareTo(d.minimum) > 0 ? minimum : d.minimum;
        }

        if (Objects.equals(maximum, d.maximum))
            inclut_max = !(!inclut_max || !d.inclut_max);
        else if (maximum == null)
            maximum = d.maximum;
        else if (d.maximum != null)
            maximum = maximum.compareTo(d.maximum) < 0 ? maximum : d.maximum;
    }

    public boolean correspond(Version version) {
        return (minimum == null || version.compareTo(minimum) >= (inclut_min ? 0 : 1))
                && (maximum == null || version.compareTo(maximum) <= (inclut_max ? 0 : -1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionIntervalle that = (VersionIntervalle) o;
        return inclut_min == that.inclut_min &&
                inclut_max == that.inclut_max &&
                Objects.equals(minimum, that.minimum) &&
                Objects.equals(maximum, that.maximum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minimum, maximum, inclut_min, inclut_max);
    }

    @Override
    public String toString() {
        return (minimum != null ? (inclut_min ? '[' : '(') + minimum.toString() : '(') + ","
                + (maximum != null ? maximum.toString() + (inclut_max ? ']' : ')') : ')');
    }

    public static class VersionIntervalleFormatException extends IllegalArgumentException {
        public VersionIntervalleFormatException(String texte) {
            super("Interval non reconnue: '" + texte + "'");
        }
    }
}
