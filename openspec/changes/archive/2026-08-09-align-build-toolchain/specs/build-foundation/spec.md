## MODIFIED Requirements

### Requirement: Mutation coverage of production code

All production code SHALL be covered by mutation testing at a 100% mutation, coverage and test
strength threshold, and `check` SHALL fail below it. No production package SHALL be excluded, and no
package SHALL carry a lower threshold than any other.

Mutation testing SHALL be enabled per module by that module's own declaration rather than applied
automatically, so that the build conventions can be shared with projects that do not want it. Because
a module can then be excluded by saying nothing, every module carrying production classes SHALL
declare it, and the thresholds SHALL be defined once for all of them rather than per module.

#### Scenario: Surviving mutant fails the build

- **WHEN** a mutation of any production class survives the unit tests
- **THEN** `check` fails

#### Scenario: No production package is excluded

- **WHEN** mutation testing runs
- **THEN** the calculation core, the git access layer, the settings plugin and its extension are all
  mutated

#### Scenario: One threshold applies everywhere

- **WHEN** the mutation testing configuration is inspected
- **THEN** it declares a single set of thresholds covering every production package

#### Scenario: Every module with production classes enables mutation testing

- **WHEN** the build's modules are inspected
- **THEN** each module containing production classes declares mutation testing itself, and
  mutation testing runs for it

#### Scenario: Modules without production classes are not mutated

- **WHEN** a module contains only tests, or carries no production classes
- **THEN** mutation testing does not run for it, and its absence does not fail the build

#### Scenario: Thresholds are not restated per module

- **WHEN** a module enables mutation testing
- **THEN** it inherits the shared thresholds without declaring any of its own
