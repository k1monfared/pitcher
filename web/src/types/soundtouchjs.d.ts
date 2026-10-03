declare module "soundtouchjs" {
  export class PitchShifter {
    constructor(
      context: AudioContext,
      buffer: AudioBuffer,
      bufferSize?: number,
      onEnd?: () => void,
    );
    timePlayed: number;
    sourcePosition: number;
    duration: number;
    sampleRate: number;
    readonly node: AudioNode;
    percentagePlayed: number;
    pitch: number;
    pitchSemitones: number;
    rate: number;
    tempo: number;
    connect(toNode: AudioNode): void;
    disconnect(): void;
  }
}
