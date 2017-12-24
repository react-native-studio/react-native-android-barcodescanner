## react-native-android-barcodescanner [![npm version](https://badge.fury.io/js/react-native-android-barcodescanner.svg)](https://badge.fury.io/js/react-native-android-barcodescanner)
## 第一步
工程目录下运行 npm install --save react-native-android-barcodescanner 或者 yarn add react-native-android-barcodescanner(已经安装了yarn)

### 注意：
version 0.1.1适用于大于等于rn0.44和小于0.47<br/>
version 0.1.5适用于rn0.47及以上
加入```<uses-permission android:name="android.permission.CAMERA" />```权限
## 第二步
运行 react-native link react-native-android-barcodescanner
## 第三部使用
在工程中导入：
```bash
import ZbarScannerView from 'react-native-android-barcodescanner';
<ZbarScannerView
            style={{flex:1}}
            onScanResultReceived={(data)=>{ToastAndroid.show(JSON.stringify(data),ToastAndroid.SHORT)}}
            hintText={''}
            renderTopBarView={() => <View></View>}
            renderBottomMenuView={() => <View></View>}
        />
<br/>
```
## Props

![](https://github.com/MarnoDev/AC-QRCode-RN/blob/master/screenshots/ac-qrcode-props.jpg)

|Prop|Type|Default|Optional|
|:--:|:--:|:--:|:--:|
|maskColor|string|#0000004D|true|
|borderColor|string|#000000|true|
|cornerColor|string|#000000|true|
|borderWidth|number|0|true|
|cornerBorderWidth|number|4|true|
|cornerBorderLength|number|20|true|
|rectHeight|number|200|true|
|rectWidth|number|200|true|
|isCornerOffset|bool|false|true|
|cornerOffsetSize|number|0|true|
|bottomMenuHeight|number|0|true|
|scanBarAnimateTime|number|2500|true|
|scanBarColor|string|#22ff00|true|
|scanBarImage|any|null|true|
|scanBarHeight|number|1.5|true|
|scanBarMargin|number|6|true|
|hintText|string|将二维码/条码放入框内，</br>即可自动扫描|true|
|hintTextStyle|object|{ color: '#fff', </br>fontSize: 14,</br>backgroundColor:'transparent'}|true|
|hintTextPosition|number|130|true|
|isShowScanBar|bool|true|true|
|bottomMenuStyle|object|-|true|
|renderTopBarView|func|-|flase|
|renderBottomMenuView|func|-|false|
|onScanResultReceived|func|-|false|
