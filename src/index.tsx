import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-billie-camera2' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const BillieCamera = NativeModules.BillieCamera2
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
      console.log("startCamerastartCamerastartCamera111" + BillieCamera.startCamera)
      let result = await BillieCamera.startCamera(enableVideo);
      console.log("startCamerastartCamerastartCamera111 = " + result)
      return JSON.parse(result) as MediaModel;
    }
    

    export async function chooseAvatar(width:number, height:number): Promise<MediaModel> {
      console.log("startCamerastartCamerastartCamera111" + BillieCamera.startCamera)
      let result = await BillieCamera.chooseAvatar(width, height);
      console.log("startCamerastartCamerastartCamera111 = " + result)
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