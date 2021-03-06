package org.tron.core.net.message;

import java.util.List;
import org.tron.core.Sha256Hash;
import org.tron.protos.Protocal.Inventory;
import org.tron.protos.Protocal.Inventory.InventoryType;

public class BlockInventoryMessage extends InventoryMessage {

  public BlockInventoryMessage(byte[] packed) {
    super(packed);
  }

  public BlockInventoryMessage(Inventory inv) {
    super(inv);
  }

  public BlockInventoryMessage(List<Sha256Hash> hashList) {
    super(hashList, InventoryType.BLOCK);
  }

  @Override
  public MessageTypes getType() {
    return MessageTypes.BLOCK_INVENTORY;
  }


}
