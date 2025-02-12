// SPDX-License-Identifier: Apache-2.0

package chiseltest.simulator

import chiseltest.utils.CaptureStdout
import org.scalatest.flatspec.AnyFlatSpec

class TreadleBasicCompliance extends BasicCompliance(TreadleSimulator)
class TreadleStepCompliance extends StepCompliance(TreadleSimulator)
class TreadlePeekPokeCompliance extends PeekPokeCompliance(TreadleSimulator)
class TreadleWaveformCompliance extends WaveformCompliance(TreadleSimulator)
class TreadleCoverageCompliance extends CoverageCompliance(TreadleSimulator)
class TreadleMemoryCompliance extends MemoryCompliance(TreadleSimulator)
class TreadleStopAssertAssumeCompliance extends StopAssertAssumeCompliance(TreadleSimulator)

class TreadleSpecificTests extends AnyFlatSpec {
  behavior of "treadle2"

  private val sim = TreadleSimulator

  it should "print the version that we depend on" in {
    val (_, out) = CaptureStdout {
      sim.findVersions()
    }
    assert(out.contains("treadle is available"))
  }
}
