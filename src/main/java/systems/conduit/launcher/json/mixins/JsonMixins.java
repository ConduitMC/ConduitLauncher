package systems.conduit.launcher.json.mixins;

import java.util.ArrayList;
import java.util.List;

public class JsonMixins {

    private List<JsonMixin> mixins = new ArrayList<>();

    public List<JsonMixin> getMixins() {
        return mixins;
    }
}
