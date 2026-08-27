CREATE TABLE anchor_upgrades (
    anchor_id TEXT NOT NULL,
    upgrade_type TEXT NOT NULL,
    installed_at TEXT NOT NULL,
    PRIMARY KEY (anchor_id, upgrade_type),
    FOREIGN KEY (anchor_id) REFERENCES sanctuary_anchors(id) ON DELETE CASCADE
);

CREATE TABLE sanctuary_upgrades (
    sanctuary_id TEXT NOT NULL,
    upgrade_type TEXT NOT NULL,
    installed_at TEXT NOT NULL,
    PRIMARY KEY (sanctuary_id, upgrade_type),
    FOREIGN KEY (sanctuary_id) REFERENCES sanctuaries(id) ON DELETE CASCADE
);
