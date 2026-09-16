package hdisoft.app.qa.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionModelTest {
    @Test
    fun gsonMapsNameToConcreteActionAndAppliesDefaults() {
        val action = ActionModel.fromJson(
            """{"id":999,"name":"tap","input":{"x":102,"y":399}}"""
        )

        assertTrue(action is TapAction)
        assertTrue(action.id > 0)
        assertFalse(action.id == 999)
        assertEquals("tap", action.displayName)
        assertEquals(ActionCategory.CORE, action.category)
    }

    @Test
    fun generatedIdsAreUniqueAndIncreasing() {
        val first = ActionModel.fromJson("""{"name":"capture"}""")
        val second = ActionModel.fromJson("""{"name":"capture"}""")

        assertTrue(second.id > first.id)
    }

    @Test
    fun gsonSerializesRuntimeFieldsAndCanRoundTrip() {
        val original = ActionModel.fromJson(
            """{"name":"tap","category":"core","input":{"x":102,"y":399}}"""
        )
        original.output = true

        val json = original.toJson()
        val restored = ActionModel.fromJson(json)

        assertTrue(json.contains("\"id\":${original.id}"))
        assertTrue(json.contains("\"output\":true"))
        assertTrue(restored is TapAction)
        assertTrue(restored.id > original.id)
    }

    @Test
    fun legacyJsonStillMapsToConcreteAction() {
        val action = ActionModel.fromJson(
            """{"action":"tap","x":102,"y":399}"""
        )

        assertTrue(action is TapAction)
        assertEquals("tap", action.name)
    }

    @Test
    fun gsonMapsAndSerializesPolymorphicActionLists() {
        val actions = ActionModel.listFromJson(
            """[{"name":"open_app","input":{"query":"Calendar"}},{"name":"capture"}]"""
        )

        assertTrue(actions[0] is OpenAppAction)
        assertTrue(actions[1] is CaptureAction)
        val json = ActionModel.listToJson(actions)
        assertTrue(json.contains("\"name\":\"open_app\""))
        assertTrue(json.contains("\"name\":\"capture\""))
    }

    @Test
    fun gsonMapsSystemNavigationActionsAsCoreActions() {
        val actions = ActionModel.listFromJson(
            """[{"name":"home"},{"name":"back"},{"name":"recents"}]"""
        )

        assertTrue(actions[0] is HomeAction)
        assertTrue(actions[1] is BackAction)
        assertTrue(actions[2] is RecentsAction)
        actions.forEach { assertEquals(ActionCategory.CORE, it.category) }
    }

    @Test
    fun registryDrivesGsonAndWebStepDefinitions() {
        val waitAction = ActionModel.fromJson(
            """{"name":"wait","input":{"duration":25}}"""
        )
        val definitionsJson = ActionModel.definitionsToJson()

        assertTrue(waitAction is WaitAction)
        assertEquals(ActionCategory.CORE, waitAction.category)
        assertEquals(9, ActionRegistry.definitions().size)
        assertTrue(definitionsJson.contains("\"name\":\"wait\""))
        assertTrue(definitionsJson.contains("\"className\":\"WaitAction\""))
        assertTrue(definitionsJson.contains("\"name\":\"capture\""))
        assertTrue(definitionsJson.contains("\"output\":\"Image URL\""))
        assertTrue(definitionsJson.contains("\"output\":\"Video URL\""))
        assertTrue(definitionsJson.contains("\"name\":\"clear_recents\""))
        assertTrue(definitionsJson.contains("\"className\":\"ClearRecentsAction\""))
        assertTrue(definitionsJson.contains("\"output\":\"Clear-recents result\""))
    }

    @Test
    fun gsonMapsClearRecentsAsCoreAction() {
        val action = ActionModel.fromJson("""{"name":"clear_recents"}""")

        assertTrue(action is ClearRecentsAction)
        assertEquals(ActionCategory.CORE, action.category)
    }

    @Test
    fun stopAllIsNotAdvertisedAsExecutableBecauseAndroidDoesNotAllowItWithoutRoot() {
        val definitionsJson = ActionModel.definitionsToJson()

        assertFalse(definitionsJson.contains("\"name\":\"stop_all\""))
    }
}
