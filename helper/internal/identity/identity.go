// Package identity creates, saves, and loads the Tailcat identity behind a
// Tailcraft address.
//
// A persistent identity pins its DERP region on first use so the address it
// produces never changes across restarts. The key file uses the same JSON
// encoding of [tailcat.PrivateKey] as the tailcat CLI.
package identity

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"

	"github.com/tailscale/tailcat"
	"tailscale.com/tailcfg"
)

// Identity is a server key plus the expanded DERP region it uses.
type Identity struct {
	// Key holds the private key and the short-form connection info. Its
	// Public.RegionID is always a concrete, pinned region.
	Key tailcat.PrivateKey
	// Region is Key.Public.RegionID expanded from the DERP map.
	Region *tailcfg.DERPRegion
}

// Addr is the short Tailcat address clients use to connect. It references the
// pinned region by ID, so it stays the same for as long as the key does.
func (id *Identity) Addr() tailcat.Addr {
	ci := tailcat.ConnInfo{
		ServerPublic:      id.Key.Public.ServerPublic,
		ServerDiscoPublic: tailcat.DiscoPublicForNode(id.Key.Private),
		PresharedKey:      id.Key.Public.PresharedKey,
		RegionID:          id.Key.Public.RegionID,
	}
	return ci.Addr()
}

// Ephemeral returns a new identity in the nearest DERP region. Nothing is
// written to disk.
func Ephemeral(ctx context.Context, opts ...any) (*Identity, error) {
	key := tailcat.NewPrivateKey()
	key.Public.RegionID = -1
	return expand(ctx, key, opts...)
}

// LoadOrCreate loads the identity at path, or creates one in the nearest DERP
// region and saves it with owner-only permissions.
func LoadOrCreate(ctx context.Context, path string, opts ...any) (*Identity, error) {
	b, err := os.ReadFile(path)
	if errors.Is(err, fs.ErrNotExist) {
		id, err := Ephemeral(ctx, opts...)
		if err != nil {
			return nil, err
		}
		if err := save(path, &id.Key); err != nil {
			return nil, err
		}
		return id, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read key file: %w", err)
	}
	var key tailcat.PrivateKey
	if err := json.Unmarshal(b, &key); err != nil {
		return nil, fmt.Errorf("parse key file %s: %w", path, err)
	}
	if key.Private.IsZero() || key.Public.PresharedKey.IsZero() || key.Public.RegionID <= 0 {
		return nil, fmt.Errorf("key file %s is incomplete; delete it to create a new address", path)
	}
	if key.Public.ServerPublic.NodePublic != key.Private.Public() {
		return nil, fmt.Errorf("key file %s has mismatched keys; delete it to create a new address", path)
	}
	return expand(ctx, &key, opts...)
}

// expand resolves key's region from the DERP map. A RegionID of -1 picks the
// nearest region, which is then pinned in the returned identity.
func expand(ctx context.Context, key *tailcat.PrivateKey, opts ...any) (*Identity, error) {
	ci := key.Public
	ci.Region = nil
	if err := ci.Expand(ctx, append([]any{tailcat.ExpandForServer}, opts...)...); err != nil {
		return nil, fmt.Errorf("select DERP region: %w", err)
	}
	if len(ci.Region) != 1 || ci.Region[0].RegionID <= 0 {
		return nil, errors.New("DERP selection returned no region")
	}
	region := ci.Region[0]
	pinned := *key
	pinned.Public.Region = nil
	pinned.Public.RegionID = region.RegionID
	pinned.Public.ServerPublic = tailcat.NodePublic{NodePublic: key.Private.Public()}
	pinned.Public.ServerDiscoPublic = tailcat.DiscoPublicForNode(key.Private)
	return &Identity{Key: pinned, Region: region}, nil
}

func save(path string, key *tailcat.PrivateKey) error {
	b, err := json.MarshalIndent(key, "", "  ")
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return fmt.Errorf("create key directory: %w", err)
	}
	tmp, err := os.CreateTemp(filepath.Dir(path), ".tailcraft-key-*")
	if err != nil {
		return fmt.Errorf("create key file: %w", err)
	}
	defer os.Remove(tmp.Name())
	if err := tmp.Chmod(0o600); err != nil && !errors.Is(err, errors.ErrUnsupported) {
		tmp.Close()
		return fmt.Errorf("restrict key file: %w", err)
	}
	if _, err := tmp.Write(append(b, '\n')); err != nil {
		tmp.Close()
		return fmt.Errorf("write key file: %w", err)
	}
	if err := tmp.Close(); err != nil {
		return fmt.Errorf("write key file: %w", err)
	}
	if err := os.Rename(tmp.Name(), path); err != nil {
		return fmt.Errorf("save key file: %w", err)
	}
	return nil
}
