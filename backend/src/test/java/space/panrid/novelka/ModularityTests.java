package space.panrid.novelka;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build when a module reaches into another module's internals. */
class ModularityTests {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(NovelkaApplication.class).verify();
    }
}
