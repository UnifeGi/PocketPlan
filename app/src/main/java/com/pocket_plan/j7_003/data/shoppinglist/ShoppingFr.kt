package com.pocket_plan.j7_003.data.shoppinglist

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pocket_plan.j7_003.MainActivity
import com.pocket_plan.j7_003.R
import com.pocket_plan.j7_003.data.settings.SettingId
import com.pocket_plan.j7_003.data.settings.SettingsManager
import kotlinx.android.synthetic.main.fragment_shopping.view.*
import kotlinx.android.synthetic.main.row_category.view.*
import kotlinx.android.synthetic.main.row_item.view.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.min


class ShoppingFr : Fragment() {
    private lateinit var myActivity: MainActivity
    lateinit var myMultiShoppingFr: MultiShoppingFr
    lateinit var shoppingListInstance: ShoppingList
    lateinit var shoppingListName: String

    lateinit var myAdapter: ShoppingListAdapter

    companion object {

        var suggestSimilar: Boolean =
            SettingsManager.getSetting(SettingId.SUGGEST_SIMILAR_ITEMS) as Boolean

        //TODO remember deleted item even when changing lists
        var deletedItem: ShoppingItem? = null

        lateinit var layoutManager: LinearLayoutManager

        var offsetTop: Int = 0
        var firstPos: Int = 0
        var expandOne: Boolean = false
        var collapseCheckedSublists: Boolean = false

        @JvmStatic
        fun newInstance() =
            ShoppingFr().apply {
                arguments = Bundle().apply {
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        myAdapter = ShoppingListAdapter(activity as MainActivity, this)
        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        myActivity = activity as MainActivity
        deletedItem = null

        //load settings
        expandOne = SettingsManager.getSetting(SettingId.EXPAND_ONE_CATEGORY) as Boolean
        collapseCheckedSublists =
            SettingsManager.getSetting(SettingId.COLLAPSE_CHECKED_SUBLISTS) as Boolean

        //if expandOne Setting = true, expand one category, contract all others
        if (expandOne) {
            shoppingListInstance.forEach {
                if (shoppingListInstance.getTagIndex(it.first) == 0) {
                    if (!shoppingListInstance.isTagExpanded(it.first)) {
                        shoppingListInstance.flipExpansionState(it.first)
                    }
                } else {
                    if (shoppingListInstance.isTagExpanded(it.first)) {
                        shoppingListInstance.flipExpansionState(it.first)
                    }
                }
            }
        }

        // Inflate the layout for this fragment
        val myView = inflater.inflate(R.layout.fragment_shopping, container, false)

        //Initialize references to recycler and its adapter
        val myRecycler = myView.recycler_view_shopping

        //attach adapter to recycler and initialize parameters of recycler
        myRecycler.adapter = myAdapter
        layoutManager = LinearLayoutManager(activity)
        myRecycler.layoutManager = layoutManager
        myRecycler.setHasFixedSize(true)

        //ItemTouchHelper to support drag and drop reordering
        val itemTouchHelper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.END or ItemTouchHelper.START,
                0
            ) {
                var previousPosition: Int = -1
                var moving = false

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    val currentPosition = viewHolder.adapterPosition
                    //mark that moving has ended (to allow a new previousPosition when move is detected)
                    moving = false

                    // don't refresh item if
                    // currentPosition == -1   =>  clearView got called due to a swipe to delete
                    // currentPosition == previousPosition   =>  item was moved, but placed back to original position
                    // previousPosition == -1   =>  item was selected but not moved
                    if (currentPosition == -1 || currentPosition == previousPosition || previousPosition == -1) {
                        previousPosition = -1
                        super.clearView(recyclerView, viewHolder)
                        return
                    }

                    //save category that was moved
                    val movedCategory = shoppingListInstance[previousPosition]
                    //remove it from its previous position
                    shoppingListInstance.removeAt(previousPosition)
                    //re-add it at the current adapter position
                    shoppingListInstance.add(currentPosition, movedCategory)
                    shoppingListInstance.save()

                    //get tag of this category
                    val tag = (viewHolder as ShoppingListAdapter.CategoryViewHolder).tag
                    //get position
                    val position = viewHolder.adapterPosition
                    //get boolean if all items are checked
                    val oldAllChecked = shoppingListInstance.areAllChecked(tag)

                    //get new checked state
                    val newAllChecked = if (currentPosition > previousPosition) {
                        //if moved down, take status from above
                        shoppingListInstance.areAllChecked(shoppingListInstance[position - 1].first)
                    } else {
                        //if moved up, take status from below
                        shoppingListInstance.areAllChecked(shoppingListInstance[position + 1].first)
                    }

                    if (oldAllChecked != newAllChecked) {
                        //auto expand / collapse when checkedState changed

                        //if setting says to collapse all sub lists, the new checked state is all checked,
                        //and its currently expanded, collapse it
                        if (collapseCheckedSublists && newAllChecked && shoppingListInstance.isTagExpanded(
                                tag
                            )
                        ) {
                            shoppingListInstance.flipExpansionState(tag)
                        }

                        //if new state is all unchecked, and its currently not expanded, expand it
                        if (!newAllChecked && !shoppingListInstance.isTagExpanded(tag)) {
                            shoppingListInstance.flipExpansionState(tag)
                            //adjust other categories if setting says to only expand one
                            if (expandOne) {
                                //iterate through all categories and contract one if you find one that's expanded and not the current sublist
                                shoppingListInstance.forEach {
                                    if (shoppingListInstance.isTagExpanded(it.first) && it.first != tag) {
                                        shoppingListInstance.flipExpansionState(it.first)
                                        myAdapter.notifyItemChanged(
                                            shoppingListInstance.getTagIndex(
                                                it.first
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        //flip checked state of this category
                        shoppingListInstance.equalizeCheckedStates(tag)
                        myAdapter.notifyItemChanged(position)
                        shoppingListInstance.save()
                    }

                    super.clearView(recyclerView, viewHolder)
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
                ): Boolean {

                    if (!moving) {
                        //if not moving, save new previous position
                        previousPosition = viewHolder.adapterPosition

                        //and prevent new previous positions from being set until this move is over
                        moving = true
                    }

                    //get start and target position of item that gets dragged
                    val fromPos = viewHolder.adapterPosition
                    val toPos = target.adapterPosition

                    // animate move of category from `fromPos` to `toPos` in adapter.
                    myAdapter.notifyItemMoved(fromPos, toPos)

                    return true // true if moved, false otherwise
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    /* no-op, swiping categories is not supported */
                }
            })

        itemTouchHelper.attachToRecyclerView(myRecycler)

        return myView
    }
    /**
     * Helper function to prevent scrolling due to notifyMove
     */
    fun prepareForMove() {
        firstPos = layoutManager.findFirstVisibleItemPosition()
        offsetTop = 0
        if (firstPos >= 0) {
            val firstView = layoutManager.findViewByPosition(firstPos)
            offsetTop =
                layoutManager.getDecoratedTop(firstView!!) - layoutManager.getTopDecorationHeight(
                    firstView
                )
        }
    }

    /**
     * Helper function to prevent scrolling due to notifyMove
     */
    fun reactToMove() {
        layoutManager.scrollToPositionWithOffset(firstPos, offsetTop)
    }
}

/**
 * Adapter for categories
 */
class ShoppingListAdapter(mainActivity: MainActivity, shoppingFr: ShoppingFr) :
    RecyclerView.Adapter<ShoppingListAdapter.CategoryViewHolder>() {
    private val myFragment = shoppingFr
    private val myActivity = mainActivity
    private val round = SettingsManager.getSetting(SettingId.SHAPES_ROUND) as Boolean
    private val collapseCheckedSublists =
        SettingsManager.getSetting(SettingId.COLLAPSE_CHECKED_SUBLISTS) as Boolean
    private val moveCheckedSublistsDown =
        SettingsManager.getSetting(SettingId.MOVE_CHECKED_DOWN) as Boolean
    private val cr = myActivity.resources.getDimension(R.dimen.cornerRadius)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_category, parent, false)
        return CategoryViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        //long click listener playing shake animation to indicate moving is possible
        holder.itemView.setOnLongClickListener {
            val animationShake =
                AnimationUtils.loadAnimation(myActivity, R.anim.shake_small)
            holder.itemView.startAnimation(animationShake)
            true
        }

        //Get reference to currently used shopping list instance
        val shoppingListInstance = myFragment.shoppingListInstance

        //get Tag for current category element
        val tag = shoppingListInstance[position].first

        //set tag for view holder
        holder.tag = tag

        //get Number of unchecked items
        val numberOfItems = shoppingListInstance.getUncheckedSize(tag)

        val expanded = shoppingListInstance.isTagExpanded(tag)

        //Expand or contract recyclerview depending on its expansion state
        holder.subRecyclerView.visibility = when (expanded) {
            true -> View.VISIBLE
            false -> View.GONE
        }

        //Flip expansion arrow to show expansion state of category
        holder.itemView.ivExpand.rotation = when (expanded) {
            true -> 180f
            else -> 0f
        }

        //Sets Text name of category of sublist
        holder.tvCategoryName.text =
            myActivity.resources.getStringArray(R.array.categoryNames)[myActivity.resources.getStringArray(
                R.array.categoryCodes
            ).indexOf(tag)]

        //Sets background color of sublist according to the tag
        manageCheckedCategory(
            holder,
            myFragment.shoppingListInstance.areAllChecked(tag),
            numberOfItems,
            tag
        )

        //Setting adapter for this sublist
        val subAdapter = SublistAdapter(tag, holder, myActivity, myFragment)
        holder.subRecyclerView.adapter = subAdapter
        holder.subRecyclerView.layoutManager = LinearLayoutManager(myActivity)
        holder.subRecyclerView.setHasFixedSize(true)

        holder.subRecyclerView.setOnLongClickListener {
            val animationShake =
                AnimationUtils.loadAnimation(myActivity, R.anim.shake_small)
            holder.itemView.startAnimation(animationShake)
            true
        }

        //Initialize and attach swipe helpers to recyclerview of sublist
        val swipeHelperLeft = ItemTouchHelper(SwipeItemToDelete(ItemTouchHelper.LEFT, myFragment))
        swipeHelperLeft.attachToRecyclerView(holder.subRecyclerView)

        val swipeHelperRight = ItemTouchHelper(SwipeItemToDelete(ItemTouchHelper.RIGHT, myFragment))
        swipeHelperRight.attachToRecyclerView(holder.subRecyclerView)

        //Onclick reaction to expand / contract this sublist
        holder.itemView.clTapExpand.setOnClickListener {
            val newState: Boolean = shoppingListInstance.flipExpansionState(holder.tag)!!
            //if the item gets expanded and the setting says to only expand one
            if (newState && ShoppingFr.expandOne) {
                //iterate through all categories and contract one if you find one that's expanded and not the current sublist
                shoppingListInstance.forEach {
                    if (shoppingListInstance.isTagExpanded(it.first) && it.first != holder.tag) {
                        shoppingListInstance.flipExpansionState(it.first)
                        myFragment.myAdapter.notifyItemChanged(
                            shoppingListInstance.getTagIndex(
                                it.first
                            )
                        )
                    }
                }
            }
            notifyItemChanged(holder.adapterPosition)
            myFragment.myMultiShoppingFr.updateExpandAllIcon()
            myFragment.myMultiShoppingFr.updateCollapseAllIcon()
        }

        //long click listener on clTapExpand to ensure shake animation for long click on whole category holder
        holder.itemView.clTapExpand.setOnLongClickListener {
            val animationShake =
                AnimationUtils.loadAnimation(myActivity, R.anim.shake_small)
            holder.itemView.startAnimation(animationShake)
            true
        }


        holder.tvNumberOfItems.setOnClickListener {
            //get new checked state of all items (result)
            val newAllChecked = shoppingListInstance.equalizeCheckedStates(tag)
            if (collapseCheckedSublists) {
                if (newAllChecked && shoppingListInstance.isTagExpanded(tag)) {
                    shoppingListInstance.flipExpansionState(tag)
                } else if (!newAllChecked && !shoppingListInstance.isTagExpanded(tag)) {
                    shoppingListInstance.flipExpansionState(tag)
                    if (ShoppingFr.expandOne) {
                        //iterate through all categories and contract one if you find one that's expanded and not the current sublist
                        myFragment.shoppingListInstance.forEach {
                            if (myFragment.shoppingListInstance.isTagExpanded(it.first) && it.first != tag) {
                                myFragment.shoppingListInstance.flipExpansionState(it.first)
                                myFragment.myAdapter.notifyItemChanged(
                                    myFragment.shoppingListInstance.getTagIndex(
                                        it.first
                                    )
                                )
                            }
                        }
                    }
                }
            }

            notifyItemChanged(holder.adapterPosition)

            if (moveCheckedSublistsDown) {
                val sublistMoveInfo = myFragment.shoppingListInstance.sortCategoriesByChecked(tag)
                if (sublistMoveInfo != null) {
                    myFragment.prepareForMove()
                    myFragment.myAdapter
                        .notifyItemMoved(sublistMoveInfo.first, sublistMoveInfo.second)

                    myFragment.reactToMove()
                }

            }
            myFragment.myMultiShoppingFr.updateShoppingMenu()
        }
    }

    /**
     * manages background for category depending on its checkedState and category
     */
    fun manageCheckedCategory(
        holder: CategoryViewHolder, allChecked: Boolean,
        numberOfItems: Int, tag: String
    ) {
        if (!allChecked) {
            //get onBackGroundColor resolved
            val colorOnBackground =
                myActivity.colorForAttr(R.attr.colorOnBackGround)


            val colorCategory =
                myActivity.colorForAttr(R.attr.colorCategory)


            //get pair of color ids for right categories
            val gradientPair: Pair<Int, Int> = when (tag) {
                "So" -> Pair(R.attr.colorSonstiges, R.attr.colorSonstigesL)
                "Ob" -> Pair(R.attr.colorObstundGemüse, R.attr.colorObstundGemüseL)
                "Gt" -> Pair(R.attr.colorGetränke, R.attr.colorGetränkeL)
                "Nu" -> Pair(R.attr.colorNudelnundGetreide, R.attr.colorNudelnundGetreideL)
                "Bw" -> Pair(R.attr.colorBackwaren, R.attr.colorBackwarenL)
                "Km" -> Pair(R.attr.colorKühlregalMilch, R.attr.colorKühlregalMilchL)
                "Kf" -> Pair(R.attr.colorKühlregalFleisch, R.attr.colorKühlregalFleischL)
                "Ve" -> Pair(R.attr.colorVegan, R.attr.colorVeganL)
                "Tk" -> Pair(R.attr.colorTiefkühl, R.attr.colorTiefkühlL)
                "Ko" -> Pair(R.attr.colorKonservenFertiges, R.attr.colorKonservenFertigesL)
                "Fr" -> Pair(R.attr.colorFrühstück, R.attr.colorFrühstückL)
                "Gw" -> Pair(R.attr.colorGewürze, R.attr.colorGewürzeL)
                "Ha" -> Pair(R.attr.colorHaushalt, R.attr.colorHaushaltL)
                "Sn" -> Pair(R.attr.colorSnacks, R.attr.colorSnacksL)
                "Bz" -> Pair(R.attr.colorBackzutaten, R.attr.colorBackzutatenL)
                "Dr" -> Pair(R.attr.colorDrogerieKosmetik, R.attr.colorDrogerieKosmetikL)
                "Al" -> Pair(R.attr.colorAlkoholTabak, R.attr.colorAlkoholTabakL)
                else -> Pair(R.attr.colorBackgroundElevated, R.attr.colorBackgroundElevated)
            }

            //create gradient drawable as category background from color pair
            val myGradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    myActivity.colorForAttr(gradientPair.second),
                    myActivity.colorForAttr(gradientPair.first)
                )
            )

            //round corners if setting says so
            if (round) myGradientDrawable.cornerRadii = floatArrayOf(cr, cr, cr, cr, cr, cr, cr, cr)

            //set category background
            holder.cvCategory.background = myGradientDrawable

            //set text colors to white
            holder.tvCategoryName.setTextColor(colorCategory)
            holder.tvNumberOfItems.setTextColor(colorOnBackground)

            //display number of unchecked items
            holder.tvNumberOfItems.text = numberOfItems.toString()

            //hide checkMark
            holder.itemView.ivCheckMark.visibility = View.GONE
        } else {
            //get title color
            val colorTitle =
                myActivity.colorForAttr(R.attr.colorCheckedCategoryTitle)


            //create gradient drawable for checked category background
            val myGradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    myActivity.colorForAttr(R.attr.colorGrayL),
                    myActivity.colorForAttr(R.attr.colorGray)
                )
            )

            //round corners if setting says so
            if (round) myGradientDrawable.cornerRadii = floatArrayOf(cr, cr, cr, cr, cr, cr, cr, cr)

            //set background for checked category
            holder.cvCategory.background = myGradientDrawable

            //hint text color for checked category
            holder.tvCategoryName.setTextColor(colorTitle)

            //clear text displaying number of items
            holder.tvNumberOfItems.text = ""

            //show checkMark
            holder.itemView.ivCheckMark.visibility = View.VISIBLE
        }
    }

    /**
     * Returns amount of categories
     */
    override fun getItemCount(): Int {
        return myFragment.shoppingListInstance.size
    }

    /**
     * one instance of this class will contain one instance of row_category and meta data like
     * position also holds references to views inside the layout
     */
    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var tag: String
        val tvCategoryName: TextView = itemView.tvCategoryName
        var subRecyclerView: RecyclerView = itemView.subRecyclerView
        val cvCategory: CardView = itemView.cvCategory
        val tvNumberOfItems: TextView = itemView.tvNumberOfItems
    }
}

/**
 * Adapter for items in the sublists
 */
class SublistAdapter(
    private val tag: String,
    private val parentHolder: ShoppingListAdapter.CategoryViewHolder,
    mainActivity: MainActivity,
    shoppingFr: ShoppingFr
) : RecyclerView.Adapter<SublistAdapter.ItemViewHolder>() {
    private val myActivity = mainActivity
    private val myFragment = shoppingFr

    //boolean stating if design is round or not
    private val round = SettingsManager.getSetting(SettingId.SHAPES_ROUND) as Boolean

    //corner radius of items
    private val cr = myActivity.resources.getDimension(R.dimen.cornerRadius)

    //setting if checked sublists should be moved below unchecked sublists
    private val moveCheckedSublistsDown =
        SettingsManager.getSetting(SettingId.MOVE_CHECKED_DOWN) as Boolean

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_item, parent, false)
        return ItemViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        //longClickListener on item to ensure shake animation for category
        holder.itemView.setOnLongClickListener {
            val animationShake =
                AnimationUtils.loadAnimation(myActivity, R.anim.shake_small)
            parentHolder.itemView.startAnimation(animationShake)
            true
        }


        //get shopping item
        val item = myFragment.shoppingListInstance.getItem(tag, position)!!

        //manage onClickListener to edit item
        holder.itemView.setOnClickListener {
            myFragment.myMultiShoppingFr.editTag = tag
            myFragment.myMultiShoppingFr.editPos = position
            myFragment.myMultiShoppingFr.openEditItemDialog(item)
        }

        //set tag of surrounding category for holder
        holder.tag = tag

        //initialize checkbox
        holder.itemView.cbItem.isChecked = item.checked

        //initialize text
        holder.itemView.tvItemTitle.text = when (item.amount == "") {
            true -> item.name
            else ->
                myActivity.getString(
                    R.string.shoppingItemTitle, item.amount, item.unit, item.name
                )
        }

        //background drawable for item
        val myGradientDrawable: GradientDrawable

        //initialize text / background color and strike through flag
        if (item.checked) {
            holder.itemView.tvItemTitle.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            holder.itemView.tvItemTitle
                .setTextColor(
                    myActivity.colorForAttr(R.attr.colorHint)
                )

            myGradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    myActivity.colorForAttr(R.attr.colorGrayD),
                    myActivity.colorForAttr(R.attr.colorGrayD)
                )
            )

        } else {
            //white and no strike through otherwise
            holder.itemView.tvItemTitle.paintFlags = 0
            holder.itemView.tvItemTitle
                .setTextColor(
                    myActivity.colorForAttr(R.attr.colorOnBackGround)
                )
            myGradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    myActivity.colorForAttr(R.attr.colorBackground),
                    myActivity.colorForAttr(R.attr.colorBackground)
                )
            )

        }

        //round corners if setting says so
        if (round) myGradientDrawable.cornerRadii = floatArrayOf(cr, cr, cr, cr, cr, cr, cr, cr)

        //set background of item
        holder.itemView.background = myGradientDrawable


        //Onclick Listener for checkBox
        holder.itemView.clItemTapfield.setOnClickListener {

            //flip checkedState of item and save new position (flipItemCheckedState sorts list and returns new position)
            val newPosition = myFragment.shoppingListInstance.flipItemCheckedState(
                tag,
                holder.adapterPosition
            )

            //get number of uncheckedItems in current sublist
            val numberOfItems = myFragment.shoppingListInstance.getUncheckedSize(holder.tag)

            //If all are checked after the current item got flipped, the list has to go from color to gray
            myFragment.myAdapter.manageCheckedCategory(
                parentHolder,
                myFragment.shoppingListInstance.areAllChecked(holder.tag),
                numberOfItems,
                holder.tag
            )

            //If setting says to collapse checked sublists, and current sublist is fully checked,
            //collapse it and notify item change
            if (ShoppingFr.collapseCheckedSublists && myFragment.shoppingListInstance.areAllChecked(
                    holder.tag
                )
            ) {
                myFragment.shoppingListInstance.flipExpansionState(holder.tag)
            myFragment.myAdapter.notifyItemChanged(parentHolder.adapterPosition)
            }

            notifyItemChanged(holder.adapterPosition)


            if (newPosition > -1) {
                notifyItemMoved(holder.adapterPosition, newPosition)
            }

            //if the setting moveCheckedSublistsDown is true, sort categories by their checked state
            //and animate the move from old to new position
            if (moveCheckedSublistsDown) {
                val sublistMoveInfo = myFragment.shoppingListInstance.sortCategoriesByChecked(tag)
                if (sublistMoveInfo != null) {
                    myFragment.prepareForMove()
                    myFragment.myAdapter
                        .notifyItemMoved(sublistMoveInfo.first, sublistMoveInfo.second)

                    myFragment.reactToMove()
                }

            }
            myFragment.myMultiShoppingFr.updateShoppingMenu()
        }

        holder.itemView.clItemTapfield.setOnLongClickListener {
            val animationShake =
                AnimationUtils.loadAnimation(myActivity, R.anim.shake_small)
            parentHolder.itemView.startAnimation(animationShake)
            true
        }
    }

    override fun getItemCount(): Int {
        return myFragment.shoppingListInstance.getSublistLength(tag)
    }

    /**
    one instance of this class will contain one instance of row_item and meta data like position
    also holds references to views inside the layout
     */
    class ItemViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        lateinit var tag: String
    }
}

/**
 * ItemTouchHelper to support swipe to delete of shopping items
 */
class SwipeItemToDelete(direction: Int, shoppingFr: ShoppingFr) :
    ItemTouchHelper.SimpleCallback(0, direction) {

    private val myFragment = shoppingFr

    override fun onMove(
        recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target:
        RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        //position of item in sublist
        val position = viewHolder.adapterPosition

        //ViewHolder as ItemViewHolder
        val parsed = viewHolder as SublistAdapter.ItemViewHolder

        //position of category in shoppingList
        val tagPosition = myFragment.shoppingListInstance.getTagIndex(parsed.tag)

        //Pair of deleted item and boolean stating if sublist is empty now
        val removeInfo = myFragment.shoppingListInstance.removeItem(parsed.tag, position)

        if (removeInfo.second) {
            //entire sublist is empty => remove sublist
            myFragment.myAdapter
                .notifyItemRemoved(tagPosition)
        } else {
            //sublist changed length =>
            myFragment.myAdapter.notifyItemChanged(tagPosition)

            //check if sublist moved
            val positions = myFragment.shoppingListInstance.sortCategoriesByChecked(parsed.tag)

            if (positions != null) {
                //sublist did move => animate movement
                myFragment.prepareForMove()
                myFragment.myAdapter.notifyItemMoved(
                    positions.first, positions.second
                )
                myFragment.reactToMove()
            }
        }

        //cache deleted item to allow undo
        myFragment.myMultiShoppingFr.deletedItem = removeInfo.first

        //update options menu
        myFragment.myMultiShoppingFr.updateShoppingMenu()

    }
}

class AutoCompleteAdapter(
    context: Context,
    resource: Int,
    textViewResourceId: Int = 0,
    items: List<String> = listOf()
) : ArrayAdapter<Any>(context, resource, textViewResourceId, items) {


    internal var itemNames: MutableList<String> = mutableListOf()
    internal var suggestions: MutableList<String> = mutableListOf()
    var imWorking: Boolean = false

    init {
        itemNames = items.toMutableList()
        suggestions = ArrayList()
    }

    /**
     * Custom Filter implementation for custom suggestions we provide.
     */
    private var filter: Filter = object : Filter() {

        override fun performFiltering(inputCharSequence: CharSequence?): FilterResults {
            //convert inputCharSequence to string, remove leading or trailing white spaces and change it to lower case
            val input = inputCharSequence.toString().trim().toLowerCase(Locale.getDefault())

            val result = FilterResults()

            //don't perform search if a search is currently being performed, or input length is < 2
            if (imWorking || input.length < 2) {
                return result
            }

            //indicate that a search is being performed
            imWorking = true

            //clear suggestions from previous search
            suggestions.clear()


            //checks for every item if it starts with input (case insensitive search)
            itemNames.forEach {
                if (it.toLowerCase(Locale.getDefault())
                        .startsWith(input)
                ) {
                    suggestions.add(it)
                }
            }

            //sort all results starting with the input by length to suggest the shortest ones first
            suggestions.sortBy { it.length }

            //If less than 5 items that start with "input" have been found, add
            //items that contain "input" to the end of the list
            if (suggestions.size < 5) {
                itemNames.forEach {
                    if (it.toLowerCase(Locale.getDefault())
                            .contains(input)
                    ) {
                        if (!suggestions.contains(it)) {
                            suggestions.add(it)
                        }
                    }
                }
            }

            //if anything was found that starts with, or contains the "input", or if the setting says
            //to only show perfect matches and don't suggest similar items, return the current suggestions
            if (suggestions.isNotEmpty() || !ShoppingFr.suggestSimilar) {
                result.values = suggestions
                result.count = suggestions.size
                return result
            }

            //create a new mutable list containing all item names
            val possibles: MutableList<String> = mutableListOf()
            possibles.addAll(itemNames)

            //create map that saves itemNames with their "likelihood score"
            val withValues: MutableMap<String, Int> = mutableMapOf()

            //calculates likelihood score for every item
            possibles.forEach { itemName ->
                //index to iterate over string
                var i = 0
                //score that indicates how much this item matches the input
                var likelihoodScore = 0
                while (i < min(itemName.length, input.length)) {
                    if (itemName[i].equals(input[i], ignoreCase = true)) {
                        //increase score by 2 if this char occurs at this index
                        likelihoodScore += 2
                    } else if (itemName.toLowerCase(Locale.ROOT).contains(input[i].toLowerCase())) {
                        //increase score by 1 if this char occurs anywhere in the string
                        likelihoodScore++
                    }
                    i++
                }
                //subtract length difference from likelihood score
                likelihoodScore -= abs(itemName.length - input.length)
                //store score for this item name in the withValues map
                withValues[itemName] = likelihoodScore
            }

            //save the "withValues" map as reverse sorted list (by likelihood score), so that the
            //most likely items are at the beginning of the list
            val withValuesSortedAsList =
                withValues.toList().sortedBy { (_, value) -> value }.reversed()

            //set suggestions to a list containing only the item names
            suggestions = withValuesSortedAsList.toMap().keys.toMutableList()

            //set amount to display to minimum of 5 and current size of suggestions
            val amountToDisplay = min(suggestions.size, 5)

            //take the top "amountToDisplay" (0..5) results and return them as result
            result.values = suggestions.subList(0, amountToDisplay)
            result.count = amountToDisplay
            return result

        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {

            if (results.values == null) {
                //return nothing was found
                return
            }

            val filterList = Collections.synchronizedList(results.values as List<*>)

            if (results.count > 0) {
                clear()
                addAll(filterList)
                notifyDataSetChanged()
            }
            imWorking = false
        }
    }


    override fun getFilter(): Filter {
        return filter
    }
}
