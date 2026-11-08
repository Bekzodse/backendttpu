package uz.ttpu.inventory.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import uz.ttpu.inventory.InventoryApplication;

class ArchUnitModularityTest {

  private static final String BASE_PACKAGE = "uz.ttpu.inventory";
  private static final Set<String> DECLARED_MODULES =
      Set.of(
          "common",
          "security",
          "references",
          "asset",
          "workflow",
          "request",
          "receipt",
          "movement",
          "repair",
          "inventory",
          "finance",
          "report",
          "notification",
          "integration",
          "file",
          "audit");
  private static final Set<String> ALLOWED_LAYERS =
      Set.of("api", "service", "domain", "repository");

  private static JavaClasses importedClasses;

  @BeforeAll
  static void importProductionClasses() {
    importedClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
  }

  @Test
  @DisplayName("1. Spring Modulith ApplicationModules verification passes with zero violations")
  void springModulithApplicationModulesVerifyPasses() {
    ApplicationModules modules = ApplicationModules.of(InventoryApplication.class);
    modules.verify();
  }

  @Test
  @DisplayName(
      "2. All 16 modules exist and every class inside a module resides in api, service, domain, or"
          + " repository")
  void allModuleClassesResideStrictlyWithinFourLayers() {
    List<String> violations = new ArrayList<>();
    Set<String> discoveredModules = new java.util.HashSet<>();

    for (JavaClass javaClass : importedClasses) {
      String pkg = javaClass.getPackageName();
      if (BASE_PACKAGE.equals(pkg) || "package-info".equals(javaClass.getSimpleName())) {
        continue; // Root InventoryApplication or module package-info descriptor
      }
      Optional<ModuleAndLayer> parsed = parseModuleAndLayer(pkg);
      if (parsed.isEmpty()) {
        violations.add(javaClass.getName() + " is in invalid package: " + pkg);
        continue;
      }
      ModuleAndLayer ml = parsed.get();
      discoveredModules.add(ml.module());
      if (!DECLARED_MODULES.contains(ml.module())) {
        violations.add(javaClass.getName() + " belongs to undeclared module: " + ml.module());
      }
      if (!ALLOWED_LAYERS.contains(ml.layer())) {
        violations.add(
            javaClass.getName()
                + " belongs to invalid layer '"
                + ml.layer()
                + "' (must be one of "
                + ALLOWED_LAYERS
                + ")");
      }
    }

    assertThat(discoveredModules)
        .as("All 16 declared modules must exist in the codebase")
        .containsExactlyInAnyOrderElementsOf(DECLARED_MODULES);
    assertThat(violations)
        .as(
            "Every class in uz.ttpu.inventory.<module> must reside in api, service, domain, or"
                + " repository")
        .isEmpty();
  }

  @Test
  @DisplayName("3. API layers cannot depend on other modules' API layers")
  void apiLayersCannotDependOnOtherModulesApiLayers() {
    List<String> violations = new ArrayList<>();

    for (JavaClass sourceClass : importedClasses) {
      Optional<ModuleAndLayer> sourceInfo = parseModuleAndLayer(sourceClass.getPackageName());
      if (sourceInfo.isEmpty() || !"api".equals(sourceInfo.get().layer())) {
        continue;
      }

      for (Dependency dependency : sourceClass.getDirectDependenciesFromSelf()) {
        JavaClass targetClass = dependency.getTargetClass();
        Optional<ModuleAndLayer> targetInfo = parseModuleAndLayer(targetClass.getPackageName());
        if (targetInfo.isEmpty()) {
          continue;
        }

        boolean differentModule = !sourceInfo.get().module().equals(targetInfo.get().module());
        boolean targetIsApiLayer = "api".equals(targetInfo.get().layer());

        if (differentModule && targetIsApiLayer) {
          violations.add(
              sourceClass.getName()
                  + " (in "
                  + sourceInfo.get().module()
                  + ".api) depends on "
                  + targetClass.getName()
                  + " (in "
                  + targetInfo.get().module()
                  + ".api): "
                  + dependency.getDescription());
        }
      }
    }

    assertThat(violations).as("API layers must not depend on other modules' API layers").isEmpty();
  }

  @Test
  @DisplayName(
      "4. Modules can only communicate with other non-common modules via explicit API interfaces,"
          + " DTO records, or domain events in .api")
  void crossModuleCommunicationMustOnlyGoThroughExplicitApiInterfacesOrEvents() {
    List<String> violations = new ArrayList<>();

    for (JavaClass sourceClass : importedClasses) {
      Optional<ModuleAndLayer> sourceInfo = parseModuleAndLayer(sourceClass.getPackageName());
      if (sourceInfo.isEmpty()) {
        continue;
      }
      String sourceModule = sourceInfo.get().module();

      for (Dependency dependency : sourceClass.getDirectDependenciesFromSelf()) {
        JavaClass targetClass = dependency.getTargetClass();
        Optional<ModuleAndLayer> targetInfo = parseModuleAndLayer(targetClass.getPackageName());
        if (targetInfo.isEmpty()) {
          continue;
        }

        String targetModule = targetInfo.get().module();
        String targetLayer = targetInfo.get().layer();

        // Same module or shared 'common' kernel is permitted
        if (sourceModule.equals(targetModule) || "common".equals(targetModule)) {
          continue;
        }

        // Cross-module dependency MUST target the other module's .api package
        if (!"api".equals(targetLayer)) {
          violations.add(
              sourceClass.getName()
                  + " ("
                  + sourceModule
                  + "."
                  + sourceInfo.get().layer()
                  + ") accesses non-API layer of another module: "
                  + targetClass.getName()
                  + " ("
                  + targetModule
                  + "."
                  + targetLayer
                  + ")");
          continue;
        }

        // Target inside another module's .api package must be an explicit API interface, DTO
        // record, or Event record
        boolean isInterface = targetClass.isInterface();
        boolean isRecord = targetClass.isRecord();
        boolean isStaticMemberOfApiOrEvent =
            targetClass
                .getEnclosingClass()
                .map(enc -> enc.isInterface() || enc.isRecord())
                .orElse(false);

        if (!isInterface && !isRecord && !isStaticMemberOfApiOrEvent) {
          violations.add(
              sourceClass.getName()
                  + " ("
                  + sourceModule
                  + ") depends on concrete class "
                  + targetClass.getName()
                  + " in "
                  + targetModule
                  + ".api (only interfaces, DTO records, and domain event records are allowed)");
        }
      }
    }

    assertThat(violations)
        .as(
            "Cross-module dependencies must only target explicit API interfaces, DTO records, or"
                + " domain events in <module>.api")
        .isEmpty();
  }

  @Test
  @DisplayName(
      "5. JPA @Entity classes never leave their module and are never exposed in .api layers")
  void jpaEntitiesNeverLeaveTheirModuleAndAreOnlyInDomainLayer() {
    List<String> violations = new ArrayList<>();

    for (JavaClass javaClass : importedClasses) {
      if (!javaClass.isAnnotatedWith(Entity.class)) {
        continue;
      }

      Optional<ModuleAndLayer> entityInfo = parseModuleAndLayer(javaClass.getPackageName());
      if (entityInfo.isEmpty() || !"domain".equals(entityInfo.get().layer())) {
        violations.add("JPA @Entity " + javaClass.getName() + " must reside in a .domain package");
        continue;
      }

      String entityModule = entityInfo.get().module();

      for (Dependency incomingDep : javaClass.getDirectDependenciesToSelf()) {
        JavaClass callerClass = incomingDep.getOriginClass();
        Optional<ModuleAndLayer> callerInfo = parseModuleAndLayer(callerClass.getPackageName());
        if (callerInfo.isEmpty()) {
          continue;
        }
        if (!entityModule.equals(callerInfo.get().module())) {
          violations.add(
              "JPA @Entity "
                  + javaClass.getName()
                  + " ("
                  + entityModule
                  + ") is referenced outside its module by "
                  + callerClass.getName());
        }
        if ("api".equals(callerInfo.get().layer())) {
          violations.add(
              "JPA @Entity "
                  + javaClass.getName()
                  + " is referenced directly by API layer class "
                  + callerClass.getName());
        }
      }
    }

    assertThat(violations)
        .as(
            "JPA @Entity classes must stay encapsulated inside their owning module's"
                + " domain/service/repository layers")
        .isEmpty();
  }

  @Test
  @DisplayName(
      "6. Internal 4-layer dependency hierarchy is respected (domain does not depend on api,"
          + " service, or repository)")
  void domainLayerDoesNotDependOnApiServiceOrRepository() {
    List<String> violations = new ArrayList<>();

    for (JavaClass sourceClass : importedClasses) {
      Optional<ModuleAndLayer> sourceInfo = parseModuleAndLayer(sourceClass.getPackageName());
      if (sourceInfo.isEmpty() || !"domain".equals(sourceInfo.get().layer())) {
        continue;
      }

      for (Dependency dependency : sourceClass.getDirectDependenciesFromSelf()) {
        JavaClass targetClass = dependency.getTargetClass();
        Optional<ModuleAndLayer> targetInfo = parseModuleAndLayer(targetClass.getPackageName());
        if (targetInfo.isEmpty()) {
          continue;
        }

        String targetLayer = targetInfo.get().layer();
        if (!"domain".equals(targetLayer)) {
          violations.add(
              "Domain class "
                  + sourceClass.getName()
                  + " depends on non-domain class "
                  + targetClass.getName()
                  + " in layer '"
                  + targetLayer
                  + "'");
        }
      }
    }

    assertThat(violations)
        .as("Domain layer classes must not depend on api, service, or repository layers")
        .isEmpty();
  }

  @Test
  @DisplayName("7. Repositories are strictly internal and never accessed across module boundaries")
  void repositoriesAreOnlyAccessedByTheirOwningModule() {
    List<String> violations = new ArrayList<>();

    for (JavaClass sourceClass : importedClasses) {
      Optional<ModuleAndLayer> sourceInfo = parseModuleAndLayer(sourceClass.getPackageName());
      if (sourceInfo.isEmpty()) {
        continue;
      }

      for (Dependency dependency : sourceClass.getDirectDependenciesFromSelf()) {
        JavaClass targetClass = dependency.getTargetClass();
        Optional<ModuleAndLayer> targetInfo = parseModuleAndLayer(targetClass.getPackageName());
        if (targetInfo.isEmpty()) {
          continue;
        }

        boolean differentModule = !sourceInfo.get().module().equals(targetInfo.get().module());
        boolean targetIsRepo = "repository".equals(targetInfo.get().layer());

        if (differentModule && targetIsRepo) {
          violations.add(
              sourceClass.getName()
                  + " (in "
                  + sourceInfo.get().module()
                  + ") accesses repository of another module: "
                  + targetClass.getName()
                  + " (in "
                  + targetInfo.get().module()
                  + ".repository)");
        }
      }
    }

    assertThat(violations)
        .as("Repositories must never be accessed across module boundaries")
        .isEmpty();
  }

  @Test
  @DisplayName("8. Spring Web Controllers must reside strictly in .api packages")
  void controllersMustOnlyResideInApiPackage() {
    List<String> violations = new ArrayList<>();

    for (JavaClass javaClass : importedClasses) {
      boolean isController =
          javaClass.isAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
              || javaClass.isAnnotatedWith(org.springframework.stereotype.Controller.class);
      if (!isController) {
        continue;
      }

      Optional<ModuleAndLayer> info = parseModuleAndLayer(javaClass.getPackageName());
      if (info.isEmpty() || !"api".equals(info.get().layer())) {
        violations.add(
            "Controller "
                + javaClass.getName()
                + " must reside in an .api package but was in "
                + javaClass.getPackageName());
      }
    }

    assertThat(violations).as("Controllers must reside strictly in .api packages").isEmpty();
  }

  @Test
  @DisplayName("9. Spring Services must reside strictly in .service packages")
  void servicesMustOnlyResideInServicePackage() {
    List<String> violations = new ArrayList<>();

    for (JavaClass javaClass : importedClasses) {
      if (!javaClass.isAnnotatedWith(org.springframework.stereotype.Service.class)) {
        continue;
      }

      Optional<ModuleAndLayer> info = parseModuleAndLayer(javaClass.getPackageName());
      if (info.isEmpty() || !"service".equals(info.get().layer())) {
        violations.add(
            "Service "
                + javaClass.getName()
                + " must reside in a .service package but was in "
                + javaClass.getPackageName());
      }
    }

    assertThat(violations).as("Services must reside strictly in .service packages").isEmpty();
  }

  @Test
  @DisplayName("10. Spring Data Repositories must reside strictly in .repository packages")
  void repositoriesMustOnlyResideInRepositoryPackage() {
    List<String> violations = new ArrayList<>();

    for (JavaClass javaClass : importedClasses) {
      boolean isRepo =
          javaClass.isAssignableTo(org.springframework.data.repository.Repository.class)
              || javaClass.isAnnotatedWith(org.springframework.stereotype.Repository.class);
      if (!isRepo) {
        continue;
      }

      Optional<ModuleAndLayer> info = parseModuleAndLayer(javaClass.getPackageName());
      if (info.isEmpty() || !"repository".equals(info.get().layer())) {
        violations.add(
            "Repository "
                + javaClass.getName()
                + " must reside in a .repository package but was in "
                + javaClass.getPackageName());
      }
    }

    assertThat(violations)
        .as("Repositories must reside strictly in .repository packages")
        .isEmpty();
  }

  private static Optional<ModuleAndLayer> parseModuleAndLayer(String packageName) {
    if (!packageName.startsWith(BASE_PACKAGE + ".")) {
      return Optional.empty();
    }
    String remainder = packageName.substring((BASE_PACKAGE + ".").length());
    String[] parts = remainder.split("\\.");
    if (parts.length < 2) {
      return Optional.empty();
    }
    return Optional.of(new ModuleAndLayer(parts[0], parts[1]));
  }

  private record ModuleAndLayer(String module, String layer) {}
}
