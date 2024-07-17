import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-billie-camera2' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const BillieCamera = NativeModules.BillieCamera
  ? NativeModules.BillieCamera2
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

    export async function startCamera(enableVideo: number): Promise<MediaModel> {
      let result = await BillieCamera.startCamera(enableVideo);
      return JSON.parse(result) as MediaModel;
    }
    
    
    export type MediaModel = {
      uri: string,
      width: number,
      height: number,
      thumbUrl: string,
      duration: number,
      contentType: string
    }