package ucx

import "testing"

func TestMachineTypeSelectorDefaultsToAllCapabilities(t *testing.T) {
	node := MachineTypeSelector("machine", "Machine type", "product")

	if node.Component != "machine_type_selector" {
		t.Fatalf("expected machine_type_selector component, got %q", node.Component)
	}

	capabilities := node.Props["capabilities"]
	if capabilities.Kind != ValueList {
		t.Fatalf("expected capabilities list, got kind %v", capabilities.Kind)
	}

	if len(capabilities.List) != 3 {
		t.Fatalf("expected 3 capabilities, got %d", len(capabilities.List))
	}

	if ValueAsString(capabilities.List[0]) != string(MachineCapabilityDocker) {
		t.Fatalf("expected first capability to be docker, got %q", ValueAsString(capabilities.List[0]))
	}
	if ValueAsString(capabilities.List[1]) != string(MachineCapabilityVm) {
		t.Fatalf("expected second capability to be vm, got %q", ValueAsString(capabilities.List[1]))
	}
	if ValueAsString(capabilities.List[2]) != string(MachineCapabilityNative) {
		t.Fatalf("expected third capability to be native, got %q", ValueAsString(capabilities.List[2]))
	}
}

func TestMachineTypeSelectorPanicsForInvalidCapability(t *testing.T) {
	defer func() {
		if recover() == nil {
			t.Fatal("expected panic for invalid machine capability")
		}
	}()

	_ = MachineTypeSelector("machine", "Machine type", "product", MachineCapability("foo"))
}
