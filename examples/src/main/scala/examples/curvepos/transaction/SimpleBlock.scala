package examples.curvepos.transaction

import com.google.common.primitives.{Ints, Longs}
import examples.curvepos.transaction.SimpleBlock._
import io.circe.Json
import io.circe.syntax._
import scorex.core.block.Block
import scorex.core.block.Block._
import scorex.core.crypto.hash.FastCryptographicHash
import scorex.core.transaction.NodeViewModifier.ModifierTypeId
import scorex.core.transaction.NodeViewModifierCompanion
import scorex.core.transaction.box.proposition.PublicKey25519Proposition
import scorex.crypto.encode.Base58
import shapeless.{::, HNil}

import scala.util.Try

case class SimpleBlock(override val parentId: BlockId,
                       override val timestamp: Long,
                       generationSignature: GenerationSignature,
                       baseTarget: BaseTarget,
                       generator: PublicKey25519Proposition,
                       txs: Seq[SimpleTransaction])
  extends Block[PublicKey25519Proposition, SimpleTransaction] {

  override def transactions: Option[Seq[SimpleTransaction]] = Some(txs)

  override def companion: NodeViewModifierCompanion[SimpleBlock] = SimpleBlockCompanion

  override def id: BlockId =
    FastCryptographicHash(txs.map(_.messageToSign).reduce(_ ++ _) ++ generator.bytes)

  override type M = SimpleBlock

  override type BlockFields = BlockId :: Timestamp :: Version ::
    GenerationSignature :: BaseTarget :: PublicKey25519Proposition :: Seq[SimpleTransaction] :: HNil

  override val version: Version = 0: Byte

  override def json: Json = Map(
    "parentId" -> Base58.encode(parentId).asJson,
    "timestamp" -> timestamp.asJson,
    "generationSignature" -> Base58.encode(generationSignature).asJson,
    "baseTarget" -> baseTarget.asJson,
    "generator" -> Base58.encode(generator.pubKeyBytes).asJson,
    "txs" -> txs.map(_.json).asJson
  ).asJson

  override lazy val blockFields: BlockFields = parentId :: timestamp :: version :: generationSignature :: baseTarget :: generator :: txs :: HNil
}

object SimpleBlock {
  val GenerationSignature = 64

  type GenerationSignature = Array[Byte]

  type BaseTarget = Long
}

object SimpleBlockCompanion extends NodeViewModifierCompanion[SimpleBlock] {

  override def bytes(block: SimpleBlock): Array[ModifierTypeId] = {
    block.parentId ++
      Longs.toByteArray(block.timestamp) ++
      Array(block.version) ++
      block.generationSignature ++
      Longs.toByteArray(block.baseTarget) ++
      block.generator.pubKeyBytes ++ {
      val cntBytes = Ints.toByteArray(block.txs.size)
      block.txs.foldLeft(cntBytes) { case (bytes, tx) =>
        val txBytes = tx.companion.bytes(tx)
        bytes ++ Ints.toByteArray(txBytes.size) ++ txBytes
      }
    }

  }

  override def parse(bytes: Array[ModifierTypeId]): Try[SimpleBlock] = Try {
    val parentId = bytes.slice(0, Block.BlockIdLength)
    val timestamp = Longs.fromByteArray(bytes.slice(Block.BlockIdLength, Block.BlockIdLength + 8))
    val version = bytes.slice(Block.BlockIdLength + 8, Block.BlockIdLength + 9).head
    val s0 = Block.BlockIdLength + 9
    val generationSignature = bytes.slice(s0, s0 + SimpleBlock.GenerationSignature)
    val baseTarget = Longs.fromByteArray(bytes.slice(s0 + SimpleBlock.GenerationSignature, s0 + SimpleBlock.GenerationSignature + 8))
    val s1 = s0 + SimpleBlock.GenerationSignature + 8
    val generator = PublicKey25519Proposition(bytes.slice(s1, s1 + 32))
    val cnt = Ints.fromByteArray(bytes.slice(s1 + 32, s1 + 36))
    val s2 = s1 + 36
    val txs = (0 until cnt) map { i =>
      val bt = bytes.slice(s2 + SimpleTransaction.TransactionLength * i, s2 + SimpleTransaction.TransactionLength * i)
      SimpleTransaction.parse(bt).get
    }
    SimpleBlock(parentId, timestamp, generationSignature, baseTarget, generator, txs)
  }
}
