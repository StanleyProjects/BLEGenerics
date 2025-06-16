package sp.ax.blegenerics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

internal fun Context.getBroadcast(state: BLEGenerics.State?): Intent {
    val broadcast = Intent(BLEGenericsService.BLEGenericsStatesAction)
    broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
    broadcast.putExtra("address", state?.address)
    if (state != null) {
        val value = when (state) {
            is BLEGenerics.State.Connected -> "Connected"
            is BLEGenerics.State.Connecting -> "Connecting"
            is BLEGenerics.State.Disconnecting -> "Disconnecting"
            is BLEGenerics.State.Searching -> "Searching"
            is BLEGenerics.State.Waiting -> "Waiting"
            is BLEGenerics.State.Pairing -> "Pairing"
            is BLEGenerics.State.Unpairing -> "Unpairing"
        }
        broadcast.putExtra("state", value)
        when (state) {
            is BLEGenerics.State.Connected -> {
                broadcast.putExtra("isPaired", state.isPaired)
            }
            else -> {
                // noop
            }
        }
    }
    return broadcast
}

internal fun Context.getBroadcast(event: BLEGenerics.Event): Intent {
    val broadcast = Intent(BLEGenericsService.BLEGenericsEventsAction)
    broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
    broadcast.putExtra("address", event.address)
    val value = when (event) {
        is BLEGenerics.Event.OnConnect -> "OnConnect"
        is BLEGenerics.Event.OnDisconnect -> "OnDisconnect"
        is BLEGenerics.Event.OnPairing -> "OnPairing"
    }
    broadcast.putExtra("event", value)
    when (event) {
        is BLEGenerics.Event.OnPairing -> {
            broadcast.putExtra("isSuccess", event.isSuccess)
        }
        else -> {
            // noop
        }
    }
    return broadcast
}

internal fun Context.getBroadcast(event: BLEProfiles.Event): Intent {
    val broadcast = Intent(BLEGenericsService.BLEProfilesEventsAction)
    broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
    val value = when (event) {
        is BLEProfiles.Event.OnServices -> "OnServices"
        is BLEProfiles.Event.OnMtuChanged -> "OnMtuChanged"
        is BLEProfiles.Event.Characteristics.OnSetNotification -> "Characteristic.OnSetNotification"
        is BLEProfiles.Event.Descriptors.OnWrite -> "Descriptors.OnWrite"
        is BLEProfiles.Event.Characteristics.OnWrite -> "Characteristics.OnWrite"
        is BLEProfiles.Event.Characteristics.OnChange -> "Characteristics.OnChange"
    }
    broadcast.putExtra("event", value)
    when (event) {
        is BLEProfiles.Event.OnMtuChanged -> {
            broadcast.putExtra("value", event.value)
        }
        is BLEProfiles.Event.OnServices -> {
            broadcast.putExtra("services", event.characteristics.keys.map { it.toString() }.toTypedArray())
            event.characteristics.forEach { (service, characteristics) ->
                broadcast.putExtra("characteristics:$service", characteristics.map { it.toString() }.toTypedArray())
            }
        }
        is BLEProfiles.Event.Characteristics.OnSetNotification -> {
            broadcast.putExtra("service", event.service.toString())
            broadcast.putExtra("characteristic", event.characteristic.toString())
            broadcast.putExtra("value", event.value)
        }
        is BLEProfiles.Event.Characteristics.OnWrite -> {
            broadcast.putExtra("service", event.service.toString())
            broadcast.putExtra("characteristic", event.characteristic.toString())
            event.result.fold(
                onSuccess = { bytes ->
                    broadcast.putExtra("bytes", bytes)
                },
                onFailure = { error ->
                    broadcast.putExtra("error", error)
                }
            )
        }
        is BLEProfiles.Event.Characteristics.OnChange -> {
            broadcast.putExtra("service", event.service.toString())
            broadcast.putExtra("characteristic", event.characteristic.toString())
            broadcast.putExtra("bytes", event.bytes)
        }
        is BLEProfiles.Event.Descriptors.OnWrite -> {
            broadcast.putExtra("service", event.service.toString())
            broadcast.putExtra("characteristic", event.characteristic.toString())
            broadcast.putExtra("descriptor", event.descriptor.toString())
            event.result.fold(
                onSuccess = { bytes ->
                    broadcast.putExtra("bytes", bytes)
                },
                onFailure = { error ->
                    broadcast.putExtra("error", error)
                }
            )
        }
    }
    return broadcast
}

internal fun register(
    context: Context,
    receivers: BroadcastReceiver,
    filters: IntentFilter,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(
            receivers,
            filters,
            Context.RECEIVER_NOT_EXPORTED,
        )
    } else {
        context.registerReceiver(receivers, filters)
    }
}
