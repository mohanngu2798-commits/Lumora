plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName.set("ai_models")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
