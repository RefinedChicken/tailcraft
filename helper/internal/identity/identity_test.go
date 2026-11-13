package identity

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"testing"

	"github.com/tailscale/tailcat"
	"tailscale.com/tailcfg"
)

func testDERPMap() *tailcfg.DERPMap {
	return &tailcfg.DERPMap{Regions: map[tailcfg.DERPRegionID]*tailcfg.DERPRegion{
		7: {RegionID: 7, RegionCode: "test", Nodes: []*tailcfg.DERPNode{{Name: "7a", RegionID: 7, HostName: "derp.invalid"}}},
	}}
}

func writeKey(t *testing.T, path string, regionID tailcfg.DERPRegionID) *tailcat.PrivateKey {
	t.Helper()
	key := tailcat.NewPrivateKey()
	key.Public.RegionID = regionID
	b, err := json.Marshal(key)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, b, 0o600); err != nil {
		t.Fatal(err)
	}
	return key
}

func TestLoadPinnedKeyGivesStableAddress(t *testing.T) {
	path := filepath.Join(t.TempDir(), "server.key")
	key := writeKey(t, path, 7)
	ctx := context.Background()

	first, err := LoadOrCreate(ctx, path, testDERPMap())
	if err != nil {
		t.Fatal(err)
	}
	second, err := LoadOrCreate(ctx, path, testDERPMap())
	if err != nil {
		t.Fatal(err)
	}
	if first.Addr() != second.Addr() {
		t.Fatalf("address changed across loads: %q != %q", first.Addr(), second.Addr())
	}
	if first.Region.RegionID != 7 {
		t.Fatalf("region = %v, want 7", first.Region.RegionID)
	}
	ci, err := tailcat.ParseAddr(first.Addr())
	if err != nil {
		t.Fatal(err)
	}
	if ci.ServerPublic != key.Public.ServerPublic || ci.RegionID != 7 || !ci.PresharedKey.Equal(key.Public.PresharedKey) {
		t.Fatalf("address does not describe the saved key: %+v", ci)
	}
	if len(ci.Region) != 0 {
		t.Fatal("address embeds the full DERP region; want short form")
	}
}

func TestCreateSavesPinnedOwnerOnlyKey(t *testing.T) {
	path := filepath.Join(t.TempDir(), "nested", "server.key")
	ctx := context.Background()
	created, err := LoadOrCreate(ctx, path, testDERPMap())
	if err != nil {
		t.Fatal(err)
	}
	if created.Key.Public.RegionID != 7 {
		t.Fatalf("created key region = %v, want pinned 7", created.Key.Public.RegionID)
	}
	if runtime.GOOS != "windows" {
		st, err := os.Stat(path)
		if err != nil {
			t.Fatal(err)
		}
		if st.Mode().Perm() != 0o600 {
			t.Fatalf("key file mode = %v, want 0600", st.Mode().Perm())
		}
	}
	loaded, err := LoadOrCreate(ctx, path, testDERPMap())
	if err != nil {
		t.Fatal(err)
	}
	if created.Addr() != loaded.Addr() {
		t.Fatal("reloaded key gives a different address")
	}
}

func TestEphemeralAddressesDiffer(t *testing.T) {
	ctx := context.Background()
	a, err := Ephemeral(ctx, testDERPMap())
	if err != nil {
		t.Fatal(err)
	}
	b, err := Ephemeral(ctx, testDERPMap())
	if err != nil {
		t.Fatal(err)
	}
	if a.Addr() == b.Addr() {
		t.Fatal("two ephemeral identities share an address")
	}
}

func TestRejectsIncompleteKey(t *testing.T) {
	path := filepath.Join(t.TempDir(), "server.key")
	writeKey(t, path, -1)
	if _, err := LoadOrCreate(context.Background(), path, testDERPMap()); err == nil {
		t.Fatal("loaded a key without a pinned region")
	}
}
